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

package com.linagora.tmail.james.jmap.routes

import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import org.apache.james.jmap.core.ProblemDetails
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.{mock, never, verify, when}
import reactor.core.publisher.Mono
import reactor.netty.NettyOutbound
import reactor.netty.http.server.HttpServerResponse

trait RespondDetailsContract {
  def respondDetails(response: HttpServerResponse, details: ProblemDetails): Unit

  private val details: ProblemDetails = ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = "boom")

  @Test
  def respondDetailsShouldNotRewriteResponseWhenHeadersAlreadySent(): Unit = {
    // Reproduces https://github.com/linagora/tmail-backend/issues/2501:
    // when a download stream fails after the response has been committed, `status()` throws
    // `IllegalStateException: Status and headers already sent`. `respondDetails` must not attempt
    // to rewrite the already committed response.
    val response: HttpServerResponse = mock(classOf[HttpServerResponse])
    when(response.hasSentHeaders).thenReturn(true)
    when(response.status(any(classOf[HttpResponseStatus]))).thenThrow(new IllegalStateException("Status and headers already sent"))

    assertThatCode(() => respondDetails(response, details))
      .doesNotThrowAnyException()

    verify(response, never()).status(any(classOf[HttpResponseStatus]))
  }

  @Test
  def respondDetailsShouldWriteErrorResponseWhenHeadersNotSentYet(): Unit = {
    val response: HttpServerResponse = mock(classOf[HttpServerResponse])
    val outbound: NettyOutbound = mock(classOf[NettyOutbound])
    when(response.hasSentHeaders).thenReturn(false)
    when(response.status(any(classOf[HttpResponseStatus]))).thenReturn(response)
    when(response.header(any(), anyString())).thenReturn(response)
    when(response.sendByteArray(any())).thenReturn(outbound)
    when(outbound.`then`()).thenReturn(Mono.empty())

    respondDetails(response, details)

    verify(response).status(INTERNAL_SERVER_ERROR)
  }
}

class DownloadAllRespondDetailsTest extends RespondDetailsContract {
  override def respondDetails(response: HttpServerResponse, details: ProblemDetails): Unit =
    DownloadAllRoutes.respondDetails(response, details).block()
}

class UnauthenticatedBlobAccessRespondDetailsTest extends RespondDetailsContract {
  override def respondDetails(response: HttpServerResponse, details: ProblemDetails): Unit =
    UnauthenticatedBlobAccessDownloadRoutes.respondDetails(response, details).block()
}
