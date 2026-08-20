/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.upload

import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.function.Consumer

import io.netty.channel.ConnectTimeoutException
import io.netty.handler.codec.http.HttpHeaderNames.{ACCEPT_ENCODING, CONTENT_ENCODING, CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpMethod.GET
import io.netty.handler.codec.http.HttpResponseStatus.OK
import io.netty.handler.ssl.SslHandshakeTimeoutException
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.timeout.{ReadTimeoutException, WriteTimeoutException}
import jakarta.inject.{Inject, Singleton}
import org.apache.commons.fileupload.util.LimitedInputStream
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scala.publisher.SMono
import reactor.netty.ByteBufFlux
import reactor.netty.http.Http11SslContextSpec
import reactor.netty.http.client.{HttpClient, HttpClientResponse, HttpClientSecurityUtils}
import reactor.netty.internal.shaded.reactor.pool.{PoolAcquirePendingLimitException, PoolAcquireTimeoutException}
import reactor.netty.tcp.SslProvider

import scala.util.Try

@Singleton
class ReactorNettyRemoteFileDownloader @Inject()(configuration: UploadFromUrlConfiguration,
                                                  ssrfValidator: SsrfRemoteUrlValidator) extends RemoteFileDownloader {
  private val sslContextSpec: SslProvider.GenericSslContextSpec[_] = configuration.sourceTrustAllSslCerts match {
    case true => Http11SslContextSpec.forClient()
      .configure(builder => {
        builder.trustManager(InsecureTrustManagerFactory.INSTANCE)
        ()
      })
    case false => Http11SslContextSpec.forClient()
  }

  private val sslProvider = SslProvider.builder()
    .sslContext(sslContextSpec)
    .handlerConfigurator(HttpClientSecurityUtils.HOSTNAME_VERIFICATION_CONFIGURER)
    .build()

  private val baseClient = HttpClient.create()
    .disableRetry(true)
    .followRedirect(false)
    .secure(sslProvider)
    .responseTimeout(configuration.sourceResponseTimeout)
    .headers(headers => headers.set(ACCEPT_ENCODING, BoundedRemoteContentDecompressor.ACCEPTED_ENCODINGS))

  private val httpClient = if (configuration.usesSsrfProtection) {
    baseClient.resolver(ssrfValidator.addressResolverGroup)
  } else {
    baseClient
  }

  override def withDownloadedFile[T](remoteUrl: ValidatedRemoteUrl,
                                     maximumSize: Long)
                                    (use: DownloadedRemoteFile => SMono[T]): SMono[T] =
    SMono.defer(() =>
      validateIfNeeded(remoteUrl)
        .flatMap(_ => fetch(remoteUrl, maximumSize, use)))
      .onErrorResume {
        case exception: RemoteUploadException => SMono.error(exception)
        case exception if isPoolAcquireFailure(exception) => SMono.error(RemoteUploadCapacityException(exception))
        case exception if isTimeout(exception) => SMono.error(RemoteUploadTimeoutException(exception))
        case exception => SMono.error(RemoteUpstreamException(exception))
      }

  private def validateIfNeeded(remoteUrl: ValidatedRemoteUrl): SMono[Unit] =
    if (remoteUrl.requiresSsrfValidation) {
      ssrfValidator.validate(remoteUrl.uri)
    } else {
      SMono.just(())
    }

  private def fetch[T](remoteUrl: ValidatedRemoteUrl,
                       maximumSize: Long,
                       use: DownloadedRemoteFile => SMono[T]): SMono[T] = {
    val client = httpClient.doOnConnected(connection => BoundedRemoteContentDecompressor.install(connection, maximumSize))
    val request: HttpClient.RequestSender = client.request(GET)
      .uri(remoteUrl.uri)

    SMono.fromPublisher(request
      .response((response: HttpClientResponse, body: ByteBufFlux) =>
        handleResponse(response, body, maximumSize, use))
      .single())
  }

  private def handleResponse[T](response: HttpClientResponse,
                                body: ByteBufFlux,
                                maximumUploadSize: Long,
                                use: DownloadedRemoteFile => SMono[T]): Publisher[T] = {
    val unsupportedEncoding = hasUnsupportedEncoding(response)
    val contentLength: Option[Long] = identityContentLength(response, unsupportedEncoding)

    (response.status(), unsupportedEncoding, contentLength) match {
      case (status, _, _) if status != OK => Mono.error(RemoteUpstreamException())
      case (_, true, _) => Mono.error(RemoteUpstreamException())
      case (_, _, Some(length)) if length > maximumUploadSize => Mono.error(RemoteUploadTooLargeException(Some(length)))
      case _ => persistResponse(response, body, maximumUploadSize, use)
    }
  }

  private def persistResponse[T](response: HttpClientResponse,
                                 body: ByteBufFlux,
                                 maximumSize: Long,
                                 use: DownloadedRemoteFile => SMono[T]): Publisher[T] = {
    val contentType = Option(response.responseHeaders().get(CONTENT_TYPE))
    val decodedBody: Flux[ByteBuffer] = body.asByteArray()
      .map[ByteBuffer](bytes => ByteBuffer.wrap(bytes))
    val resourceSupplier: Callable[InputStream] = () => limitedInputStream(ReactorUtils.toInputStream(decodedBody), maximumSize)
    val sourceSupplier: java.util.function.Function[InputStream, Mono[T]] =
      content => use(DownloadedRemoteFile(contentType, content)).asJava()
    val resourceRelease: Consumer[InputStream] = content => content.close()

    Mono.using(
      resourceSupplier,
      sourceSupplier,
      resourceRelease)
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
  }

  private def limitedInputStream(content: InputStream, maximumSize: Long): InputStream =
    new LimitedInputStream(content, maximumSize) {
      override def raiseError(maximum: Long, count: Long): Unit = throw RemoteUploadTooLargeException(Some(count))
    }

  private def hasUnsupportedEncoding(response: HttpClientResponse): Boolean =
    Option(response.responseHeaders().get(CONTENT_ENCODING))
      .toSeq
      .flatMap(_.split(","))
      .map(_.trim)
      .filter(_.nonEmpty)
      .exists(encoding => !encoding.equalsIgnoreCase("identity"))

  private def identityContentLength(response: HttpClientResponse, unsupportedEncoding: Boolean): Option[Long] =
    if (unsupportedEncoding) {
      None
    } else {
      Option(response.responseHeaders().get(CONTENT_LENGTH))
        .flatMap(value => Try(value.toLong).toOption)
        .filter(_ >= 0)
    }

  private def isTimeout(exception: Throwable): Boolean = exception match {
    case _: ConnectTimeoutException |
         _: SslHandshakeTimeoutException |
         _: ReadTimeoutException |
         _: WriteTimeoutException => true
    case _ => false
  }

  private def isPoolAcquireFailure(exception: Throwable): Boolean = exception match {
    case _: PoolAcquirePendingLimitException |
         _: PoolAcquireTimeoutException => true
    case _ => false
  }
}
