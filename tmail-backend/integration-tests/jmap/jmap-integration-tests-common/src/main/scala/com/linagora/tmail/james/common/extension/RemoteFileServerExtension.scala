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

package com.linagora.tmail.james.common.extension

import com.linagora.tmail.james.jmap.upload.{AllowedRemoteSource, UploadFromUrlConfiguration}
import org.junit.jupiter.api.extension.{AfterAllCallback, BeforeAllCallback, BeforeEachCallback, ExtensionContext}
import org.mockserver.configuration.Configuration.configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response

object RemoteFileServerExtension {
  private val MOCK_SERVER_CA_CERTIFICATE: String = "org/mockserver/socket/CertificateAuthorityCertificate.pem"
  private val MOCK_SERVER_CA_PRIVATE_KEY: String = "org/mockserver/socket/PKCS8CertificateAuthorityPrivateKey.pem"
  private val SERVER_CERTIFICATE: String = "remote-file-server-certificate.pem"
  private val SERVER_PRIVATE_KEY: String = "remote-file-server-private-key.pem"
}

class RemoteFileServerExtension extends BeforeAllCallback with BeforeEachCallback with AfterAllCallback {
  import RemoteFileServerExtension._

  private var server: ClientAndServer = _

  override def beforeAll(context: ExtensionContext): Unit =
    server = startClientAndServer(configuration()
      .certificateAuthorityCertificate(MOCK_SERVER_CA_CERTIFICATE)
      .certificateAuthorityPrivateKey(MOCK_SERVER_CA_PRIVATE_KEY)
      .dynamicallyCreateCertificateAuthorityCertificate(false)
      .privateKeyPath(SERVER_PRIVATE_KEY)
      .x509CertificatePath(SERVER_CERTIFICATE)
      .preventCertificateDynamicUpdate(true)
      .proactivelyInitialiseTLS(true), 0)

  override def beforeEach(context: ExtensionContext): Unit = server.reset()

  override def afterAll(context: ExtensionContext): Unit =
    if (server != null) {
      server.close()
    }

  def uploadFromUrlConfiguration: UploadFromUrlConfiguration = UploadFromUrlConfiguration(
    enabled = true,
    allowedSources = Seq(AllowedRemoteSource.parse(source).toOption.get),
    sourceTrustAllSslCerts = true)

  def source: String = "https://localhost:" + server.getLocalPort

  def serve(path: String, content: Array[Byte], contentType: String): Unit =
    server
      .when(request()
        .withMethod("GET")
        .withPath(path))
      .respond(response()
        .withStatusCode(200)
        .withHeader("Content-Type", contentType)
        .withBody(content))

  def serveStatus(path: String, statusCode: Int): Unit =
    server
      .when(request()
        .withMethod("GET")
        .withPath(path))
      .respond(response()
        .withStatusCode(statusCode))

  def serveWithContentLength(path: String, contentLength: Long): Unit =
    server
      .when(request()
        .withMethod("GET")
        .withPath(path))
      .respond(response()
        .withStatusCode(200)
        .withHeader("Content-Length", contentLength.toString))

  def url(path: String): String = source + path
}
