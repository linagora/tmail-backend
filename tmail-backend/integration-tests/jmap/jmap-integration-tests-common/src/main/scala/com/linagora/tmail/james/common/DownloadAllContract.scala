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

import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.common.DownloadAllContract.{EMAIL_FILE_NAME, ZIP_ENTRIES, accountId}
import com.linagora.tmail.james.jmap.ZipUtil
import com.linagora.tmail.james.jmap.ZipUtil.ZipEntryData
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.{SC_FORBIDDEN, SC_NOT_FOUND, SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ALICE_ACCOUNT_ID, ANDRE, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath, MessageId}
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.{containsString, equalTo, hasKey}
import org.junit.jupiter.api.{BeforeEach, Test}

object DownloadAllContract {
  val accountId = "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
  val EMAIL_FILE_NAME = "eml/multipart_simple.eml"
  val ZIP_ENTRIES = ImmutableList.of(
    new ZipEntryData("text1",
      "-----BEGIN RSA PRIVATE KEY-----\n" +
        "MIIEogIBAAKCAQEAx7PG0+E//EMpm7IgI5Q9TMDSFya/1hE+vvTJrk0iGFllPeHL\n" +
        "A5/VlTM0YWgG6X50qiMfE3VLazf2c19iXrT0mq/21PZ1wFnogv4zxUNaih+Bng62\n" +
        "F0SyruE/O/Njqxh/Ccq6K/e05TV4T643USxAeG0KppmYW9x8HA/GvV832apZuxkV\n" +
        "i6NVkDBrfzaUCwu4zH+HwOv/pI87E7KccHYC++Biaj3\n"),
    new ZipEntryData("text2",
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDHs8bT4T/8QymbsiAjlD1MwNIXJr/WET6+9MmuTSIYWWU94csDn9WVMzRhaAbpfnSqIx8TdUtrN/ZzX2JetPSar/bU9nXAWeiC/jPFQ1qKH4GeDrYXRLKu4T8782OrGH8Jyror97TlNXhPrjdRLEB4bQqmmZhb3HwcD8a9XzfZqlm7GRWLo1WQMGt/NpQLC7jMf4fA6/+kjzsTspxwdgL74GJqPfOXOiwgLHX8CZ6/5RyTqhT6pD3MktSNWaz/zIHPNEqf5BY9CBM1TFR5w+6MDHo0gmiIsXFEJTPnfhBvHDhSjB1RI0KxUClyYrJ4fBlUVeKfnawoVcu7YvCqF4F5 quynhnn@linagora\n"),
    new ZipEntryData("text3",
      "|1|oS75OgL3vF2Gdl99CJDbEpaJ3yE=|INGqljCW1XMf4ggOQm26/BNnKGc= ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAq2A7hRGmdnm9tUDbO9IDSwBK6TbQa+PXYPCPy6rbTrTtw7PHkccKrpp0yVhp5HdEIcKr6pLlVDBfOLX9QUsyCOV0wzfjIJNlGEYsdlLJizHhbn2mUjvSAHQqZETYP81eFzLQNnPHt4EVVUh7VfDESU84KezmD5QlWpXLmvU31/yMf+Se8xhHTvKSCZIFImWwoG6mbUoWf9nzpIoaSjB+weqqUUmpaaasXVal72J+UX2B+2RPW3RcT0eOzQgqlJL3RKrTJvdsjE3JEAvGq3lGHSZXyN6m5U4hpph9uOv54aHc4Xr8jhAa/SX5MJ\n")
  )
}

trait DownloadAllContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, BOB_PASSWORD)
      .addUser(CEDRIC.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  def randomMessageId: MessageId

  @Test
  def downloadAll(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream(EMAIL_FILE_NAME)))
      .getMessageId


    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .contentType("application/zip")
      .header("cache-control", "private, immutable, max-age=31536000")
      .extract
      .body
      .asInputStream()

    assertThat(ZipUtil.readZipData(response))
      .containsExactlyInAnyOrderElementsOf(ZIP_ENTRIES)
  }

  @Test
  def downloadAllShouldNotIncludeInlineAttachments(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mixed-inline-and-normal-attachments.eml")))
      .getMessageId

    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .contentType("application/zip")
      .header("cache-control", "private, immutable, max-age=31536000")
      .extract
      .body
      .asInputStream()

    assertThat(ZipUtil.readZipData(response))
      .containsExactlyInAnyOrderElementsOf(ImmutableList.of(
        new ZipEntryData("text1",
          "-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIIEogIBAAKCAQEAx7PG0+E//EMpm7IgI5Q9TMDSFya/1hE+vvTJrk0iGFllPeHL\n" +
            "A5/VlTM0YWgG6X50qiMfE3VLazf2c19iXrT0mq/21PZ1wFnogv4zxUNaih+Bng62\n" +
            "F0SyruE/O/Njqxh/Ccq6K/e05TV4T643USxAeG0KppmYW9x8HA/GvV832apZuxkV\n" +
            "i6NVkDBrfzaUCwu4zH+HwOv/pI87E7KccHYC++Biaj3\n")))
  }

  @Test
  def downloadShouldAddExtraNumberToFileNameWhenAttachmentNamesAreDuplicated(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/multipart_simple_with_duplicated_attachment_name.eml")))
      .getMessageId


    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .contentType("application/zip")
      .header("cache-control", "private, immutable, max-age=31536000")
      .extract
      .body
      .asInputStream()

    val list = ZipUtil.readZipData(response)
    assertThat(list.stream()
        .filter(zipEntryData => !zipEntryData.fileName().contains("."))
        .map(zipEntryData => zipEntryData.fileName())
        .toList)
      .containsExactlyInAnyOrder("text", "text_1", "text_2")

    assertThat(list.stream()
        .filter(zipEntryData => !zipEntryData.fileName().contains("."))
        .map(zipEntryData => zipEntryData.content())
        .toList)
      .containsExactlyInAnyOrder("-----BEGIN RSA PRIVATE KEY-----\n" +
        "MIIEogIBAAKCAQEAx7PG0+E//EMpm7IgI5Q9TMDSFya/1hE+vvTJrk0iGFllPeHL\n" +
        "A5/VlTM0YWgG6X50qiMfE3VLazf2c19iXrT0mq/21PZ1wFnogv4zxUNaih+Bng62\n" +
        "F0SyruE/O/Njqxh/Ccq6K/e05TV4T643USxAeG0KppmYW9x8HA/GvV832apZuxkV\n" +
        "i6NVkDBrfzaUCwu4zH+HwOv/pI87E7KccHYC++Biaj3\n",
        "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDHs8bT4T/8QymbsiAjlD1MwNIXJr/WET6+9MmuTSIYWWU94csDn9WVMzRhaAbpfnSqIx8TdUtrN/ZzX2JetPSar/bU9nXAWeiC/jPFQ1qKH4GeDrYXRLKu4T8782OrGH8Jyror97TlNXhPrjdRLEB4bQqmmZhb3HwcD8a9XzfZqlm7GRWLo1WQMGt/NpQLC7jMf4fA6/+kjzsTspxwdgL74GJqPfOXOiwgLHX8CZ6/5RyTqhT6pD3MktSNWaz/zIHPNEqf5BY9CBM1TFR5w+6MDHo0gmiIsXFEJTPnfhBvHDhSjB1RI0KxUClyYrJ4fBlUVeKfnawoVcu7YvCqF4F5 quynhnn@linagora\n",
        "|1|oS75OgL3vF2Gdl99CJDbEpaJ3yE=|INGqljCW1XMf4ggOQm26/BNnKGc= ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAq2A7hRGmdnm9tUDbO9IDSwBK6TbQa+PXYPCPy6rbTrTtw7PHkccKrpp0yVhp5HdEIcKr6pLlVDBfOLX9QUsyCOV0wzfjIJNlGEYsdlLJizHhbn2mUjvSAHQqZETYP81eFzLQNnPHt4EVVUh7VfDESU84KezmD5QlWpXLmvU31/yMf+Se8xhHTvKSCZIFImWwoG6mbUoWf9nzpIoaSjB+weqqUUmpaaasXVal72J+UX2B+2RPW3RcT0eOzQgqlJL3RKrTJvdsjE3JEAvGq3lGHSZXyN6m5U4hpph9uOv54aHc4Xr8jhAa/SX5MJ\n")

    assertThat(list.stream()
        .filter(zipEntryData => zipEntryData.fileName().contains("."))
        .map(zipEntryData => zipEntryData.fileName())
        .toList)
      .containsExactlyInAnyOrder("text.txt", "text_1.txt")

    assertThat(list.stream()
        .filter(zipEntryData => zipEntryData.fileName().contains("."))
        .map(zipEntryData => zipEntryData.content())
        .toList)
      .containsExactlyInAnyOrder("-----BEGIN RSA PRIVATE KEY-----\n" +
        "MIIEogIBAAKCAQEAx7PG0+E//EMpm7IgI5Q9TMDSFya/1hE+vvTJrk0iGFllPeHL\n" +
        "A5/VlTM0YWgG6X50qiMfE3VLazf2c19iXrT0mq/21PZ1wFnogv4zxUNaih+Bng62\n" +
        "F0SyruE/O/Njqxh/Ccq6K/e05TV4T643USxAeG0KppmYW9x8HA/GvV832apZuxkV\n" +
        "i6NVkDBrfzaUCwu4zH+HwOv/pI87E7KccHYC++Biaj3\n",
        "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDHs8bT4T/8QymbsiAjlD1MwNIXJr/WET6+9MmuTSIYWWU94csDn9WVMzRhaAbpfnSqIx8TdUtrN/ZzX2JetPSar/bU9nXAWeiC/jPFQ1qKH4GeDrYXRLKu4T8782OrGH8Jyror97TlNXhPrjdRLEB4bQqmmZhb3HwcD8a9XzfZqlm7GRWLo1WQMGt/NpQLC7jMf4fA6/+kjzsTspxwdgL74GJqPfOXOiwgLHX8CZ6/5RyTqhT6pD3MktSNWaz/zIHPNEqf5BY9CBM1TFR5w+6MDHo0gmiIsXFEJTPnfhBvHDhSjB1RI0KxUClyYrJ4fBlUVeKfnawoVcu7YvCqF4F5 quynhnn@linagora\n")
  }

  @Test
  def downloadShouldFailWhenUnauthenticated(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream(EMAIL_FILE_NAME)))
      .getMessageId

    `given`
      .auth().none()
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_UNAUTHORIZED)
      .header("WWW-Authenticate", "Basic realm=\"simple\", Bearer realm=\"JWT\"")
      .body("status", equalTo(401))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("No valid authentication methods provided"))
  }

  @Test
  def downloadShouldSucceedWhenAddedRightACL(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream(EMAIL_FILE_NAME)))
      .getMessageId
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(path, BOB.asString(), new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .contentType("application/zip")
      .extract
      .body
      .asInputStream()

    assertThat(ZipUtil.readZipData(response))
      .containsExactlyInAnyOrderElementsOf(ZIP_ENTRIES)
  }

  @Test
  def downloadingOtherPeopleMessageAttachmentsShouldFail(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream(EMAIL_FILE_NAME)))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadingInOtherAccountsShouldFail(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream(EMAIL_FILE_NAME)))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/$ALICE_ACCOUNT_ID/${messageId.serialize}")
    .`then`
      .statusCode(SC_FORBIDDEN)
      .body("status", equalTo(403))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("You cannot download in others accounts"))
  }

  @Test
  def downloadShouldSucceedWhenDelegatedAccount(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream(EMAIL_FILE_NAME)))
      .getMessageId

    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ANDRE, BOB)

    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/${Fixture.ANDRE_ACCOUNT_ID}/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .contentType("application/zip")
      .extract
      .body
      .asInputStream()

    assertThat(ZipUtil.readZipData(response))
      .containsExactlyInAnyOrderElementsOf(ZIP_ENTRIES)
  }

  @Test
  def downloadShouldFailWhenNotDelegatedAccount(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(ANDRE)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream(EMAIL_FILE_NAME)))
      .getMessageId

    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(ANDRE, CEDRIC)

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/${Fixture.ANDRE_ACCOUNT_ID}/${messageId.serialize()}")
    .`then`
      .statusCode(SC_FORBIDDEN)
      .body("status", equalTo(403))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("You cannot download in others accounts"))
  }

  @Test
  def userCanSpecifyNameWhenDownloading(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream(EMAIL_FILE_NAME)))
      .getMessageId

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .queryParam("name", "gabouzomeuh")
    .when
      .get(s"/downloadAll/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .header("Content-Disposition", containsString("filename=\"gabouzomeuh\""))
  }

  @Test
  def downloadMessageShouldDiscardNameWhenNotSuppliedByTheClient(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream(EMAIL_FILE_NAME)))
      .getMessageId

    val contentDisposition = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/$accountId/${messageId.serialize()}")
    .`then`
      .statusCode(SC_OK)
      .extract().header("Content-Disposition")

    assertThat(contentDisposition).isNullOrEmpty()
  }

  @Test
  def downloadNotExistingMessage(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/downloadAll/$accountId/${randomMessageId.serialize()}")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def downloadInvalidPart(server: GuiceJamesServer): Unit = {
    val path = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)

    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .when
      .get(s"/downloadAll/$accountId/invalid")
      .`then`
      .statusCode(SC_NOT_FOUND)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The resource could not be found"))
  }

  @Test
  def shouldReturnCapabilityInSessionRoute(): Unit =
    `given`()
      .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:downloadAll"))
      .body("capabilities.'com:linagora:params:downloadAll'.endpoint", equalTo("http://localhost/downloadAll/{accountId}/{emailId}?name={name}"))
}
