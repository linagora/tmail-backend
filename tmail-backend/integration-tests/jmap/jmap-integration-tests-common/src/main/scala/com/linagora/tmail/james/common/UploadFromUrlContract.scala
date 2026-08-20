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

package com.linagora.tmail.james.common

import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import java.util.{Base64, UUID}

import com.linagora.tmail.james.common.extension.RemoteFileServerExtension
import io.netty.handler.codec.http.HttpHeaderNames.{ACCEPT, AUTHORIZATION, CONTENT_LOCATION, CONTENT_TYPE}
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.builder.ResponseBuilder
import io.restassured.config.EncoderConfig.encoderConfig
import io.restassured.config.RestAssuredConfig.newConfig
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.{SC_BAD_GATEWAY, SC_BAD_REQUEST, SC_CREATED, SC_FORBIDDEN, SC_NOT_FOUND, SC_OK, SC_REQUEST_TOO_LONG, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object UploadFromUrlContract {
  private val CAPABILITY: String = "com:linagora:params:jmap:upload:from-url"

  case class TestContext(username: Username, accountId: String)

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait UploadFromUrlContract {
  import UploadFromUrlContract._

  def remoteFileServer: RemoteFileServerExtension

  def username: Username = currentContext.get().username

  def accountId: String = currentContext.get().accountId

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val testUsername = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    currentContext.set(TestContext(
      username = testUsername,
      accountId = AccountId.from(testUsername).toOption.get.id.value))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(testUsername.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(testUsername, BOB_PASSWORD)))
      .build()
  }

  @Test
  def sessionShouldAdvertiseUploadFromUrlCapability(): Unit = {
    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when()
      .get("/session")
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .body(s"capabilities.'$CAPABILITY'.allowedSources", Matchers.contains(remoteFileServer.source))
      .body(s"capabilities.'$CAPABILITY'.uploadUrl", equalTo("http://localhost/upload-from-url/{accountId}"))
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def shouldImportRemoteFileAsAUsableJmapAttachment(server: GuiceJamesServer): Unit = {
    // Serve a Drive-like remote file over HTTPS.
    val remotePath = "/files/report.pdf"
    val remoteContent: Array[Byte] = "Content fetched from Twake Drive".getBytes(StandardCharsets.UTF_8)
    remoteFileServer.serve(remotePath, remoteContent, "application/pdf")

    // Import the remote file as a JMAP upload blob.
    val blobId: String = `given`()
      .basePath("")
      .config(newConfig.encoderConfig(encoderConfig.appendDefaultContentCharsetToContentTypeIfUndefined(false)))
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header(CONTENT_LOCATION.toString, remoteFileServer.url(remotePath))
      .header(CONTENT_TYPE.toString, "application/pdf")
    .when()
      .post(s"/upload-from-url/$accountId")
    .`then`()
      .statusCode(SC_CREATED)
      .contentType(JSON)
      .body("accountId", equalTo(accountId))
      .body("type", equalTo("application/pdf"))
      .body("size", equalTo(remoteContent.length))
      .body("blobId", Matchers.notNullValue())
      .extract()
      .path("blobId")

    assertThat(blobId).startsWith("uploads-")

    // Verify that the imported blob can be downloaded with its content type and bytes preserved.
    val downloadedContent: Array[Byte] = `given`()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when()
      .get(s"/download/$accountId/$blobId")
    .`then`()
      .statusCode(SC_OK)
      .contentType("application/pdf")
      .extract()
      .body()
      .asByteArray()

    assertThat(downloadedContent).isEqualTo(remoteContent)

    // Use the imported blob as an attachment when creating an email.
    val mailboxId: String = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(username))
      .serialize()
    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Email/set",
           |    {
           |      "accountId": "$accountId",
           |      "create": {
           |        "remote-file": {
           |          "mailboxIds": {"$mailboxId": true},
           |          "keywords": {},
           |          "subject": "Remote attachment",
           |          "from": [{"email": "${username.asString}"}],
           |          "to": [{"email": "recipient@localhost"}],
           |          "attachments": [{
           |            "blobId": "$blobId",
           |            "type": "application/pdf",
           |            "name": "report.pdf",
           |            "disposition": "attachment"
           |          }]
           |        }
           |      }
           |    },
           |    "c1"
           |  ]]
           |}""".stripMargin)
    .when()
      .post()
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].created.'remote-file'", Matchers.notNullValue())
  }

  @Test
  def shouldUseRemoteContentTypeWhenRequestContentTypeIsMissing(server: GuiceJamesServer): Unit = {
    // Serve a remote file that advertises its media type.
    val remotePath = "/files/content-type-from-source.pdf"
    val remoteContent: Array[Byte] = "remote content type".getBytes(StandardCharsets.UTF_8)
    remoteFileServer.serve(remotePath, remoteContent, "application/pdf")

    // Send a raw authenticated request so no client library adds a Content-Type hint.
    val credentials: String = Base64.getEncoder.encodeToString(s"${username.asString()}:$BOB_PASSWORD".getBytes(StandardCharsets.UTF_8))
    val jmapPort: Int = server.getProbe(classOf[JmapGuiceProbe]).getJmapPort.getValue
    val request = HttpRequest.newBuilder(URI.create(s"http://localhost:$jmapPort/upload-from-url/$accountId"))
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header(AUTHORIZATION.toString, s"Basic $credentials")
      .header(CONTENT_LOCATION.toString, remoteFileServer.url(remotePath))
      .POST(BodyPublishers.noBody())
      .build()
    val response: HttpResponse[String] = HttpClient.newHttpClient().send(request, BodyHandlers.ofString(StandardCharsets.UTF_8))

    // Verify that the upload metadata uses the media type returned by the remote source.
    new ResponseBuilder()
        .setStatusCode(response.statusCode())
        .setContentType(response.headers().firstValue(CONTENT_TYPE.toString).orElseThrow())
        .setBody(response.body())
        .build()
      .`then`()
        .statusCode(SC_CREATED)
        .contentType(JSON)
        .body("accountId", equalTo(accountId))
        .body("type", equalTo("application/pdf"))
        .body("size", equalTo(remoteContent.length))
        .body("blobId", Matchers.notNullValue())
  }

  @Test
  def shouldRejectMissingContentLocation(): Unit = {
    // Submit an otherwise valid upload request without identifying a remote source.
    `given`()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when()
      .post(s"/upload-from-url/$accountId")

    // Verify that the route rejects the incomplete request.
    .`then`()
      .statusCode(SC_BAD_REQUEST)
      .contentType(JSON)
      .body("status", equalTo(SC_BAD_REQUEST))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The upload-from-URL request is invalid"))
  }

  @Test
  def shouldRejectSourceOutsideAllowedSources(): Unit = {
    // Submit an upload request whose remote source does not match the advertised allowlist.
    `given`()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header(CONTENT_LOCATION.toString, "https://example.com/file")
    .when()
      .post(s"/upload-from-url/$accountId")

    // Verify that the source policy rejects the URL before downloading it.
    .`then`()
      .statusCode(SC_BAD_REQUEST)
      .contentType(JSON)
      .body("status", equalTo(SC_BAD_REQUEST))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The remote URL is not allowed"))
  }

  @Test
  def shouldReturnBadGatewayWhenRemoteReturnsNonSuccess(): Unit = {
    // Configure the remote source to reject the download request.
    val remotePath = "/files/not-found"
    remoteFileServer.serveStatus(remotePath, SC_NOT_FOUND)

    // Ask the backend to import the unavailable remote file.
    `given`()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header(CONTENT_LOCATION.toString, remoteFileServer.url(remotePath))
    .when()
      .post(s"/upload-from-url/$accountId")

    // Verify that the upstream failure is exposed as a gateway error.
    .`then`()
      .statusCode(SC_BAD_GATEWAY)
      .contentType(JSON)
      .body("status", equalTo(SC_BAD_GATEWAY))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The remote file could not be downloaded"))
  }

  @Test
  def shouldRejectOversizedRemoteFile(): Unit = {
    // Configure the remote source to announce a file larger than the upload limit.
    val remotePath = "/files/oversized"
    remoteFileServer.serveWithContentLength(remotePath, Long.MaxValue)

    // Ask the backend to import the oversized remote file.
    `given`()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header(CONTENT_LOCATION.toString, remoteFileServer.url(remotePath))
    .when()
      .post(s"/upload-from-url/$accountId")

    // Verify that the backend rejects it before consuming the body.
    .`then`()
      .statusCode(SC_REQUEST_TOO_LONG)
      .contentType(JSON)
      .body("status", equalTo(SC_REQUEST_TOO_LONG))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The remote file exceeds the upload size limit"))
  }

  @Test
  def uploadFromUrlShouldRejectUnauthenticatedRequest(): Unit = {
    // Submit an upload request without the credentials from the shared request specification.
    `given`()
      .auth().none()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header(CONTENT_LOCATION.toString, remoteFileServer.url("/files/report.pdf"))
    .when()
      .post(s"/upload-from-url/$accountId")

    // Verify that authentication is required before processing the remote source.
    .`then`()
      .statusCode(SC_UNAUTHORIZED)
      .header("WWW-Authenticate", "Basic realm=\"simple\", Bearer realm=\"JWT\"")
      .contentType(JSON)
      .body("status", equalTo(SC_UNAUTHORIZED))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("Authentication failed"))
  }

  @Test
  def shouldRejectUploadToNonDelegatedAccount(server: GuiceJamesServer): Unit = {
    // Create a target account to which the authenticated user has no delegated access.
    val targetUsername = Username.fromLocalPartWithDomain(s"alice${UUID.randomUUID().toString.replace("-", "").take(8)}", DOMAIN)
    val targetAccountId = AccountId.from(targetUsername).toOption.get.id.value
    server.getProbe(classOf[DataProbeImpl]).addUser(targetUsername.asString(), BOB_PASSWORD)

    // Request an upload into that unrelated account as the original authenticated user.
    `given`()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header(CONTENT_LOCATION.toString, remoteFileServer.url("/files/report.pdf"))
    .when()
      .post(s"/upload-from-url/$targetAccountId")

    // Verify that account authorization fails before the remote file is downloaded.
    .`then`()
      .statusCode(SC_FORBIDDEN)
      .contentType(JSON)
      .body("status", equalTo(SC_FORBIDDEN))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("Upload to this account is forbidden"))
  }

}
