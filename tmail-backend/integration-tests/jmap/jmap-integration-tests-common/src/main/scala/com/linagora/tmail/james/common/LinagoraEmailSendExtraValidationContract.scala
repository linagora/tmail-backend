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

import java.util.UUID

import com.linagora.tmail.james.common.LinagoraEmailSendExtraValidationContract.DESCRIPTION
import com.linagora.tmail.james.common.LinagoraEmailSendMethodContract.bobSendsAMailToAndre
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.method.EmailSubmissionSetValidation
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.apache.mailet.Mail
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

object LinagoraEmailSendExtraValidationContract {
  val DESCRIPTION: String = "Rejected by the custom validation"
}

class RejectAllEmailSendValidation extends EmailSubmissionSetValidation {
  override def validate(mail: Mail, mailboxSession: MailboxSession): SMono[Option[SetError]] =
    SMono.just(Some(SetError.invalidArguments(SetErrorDescription(DESCRIPTION))))
}

/**
 * `Email/send` behaviour when the server declares an `EmailSubmissionSetValidation` that rejects every mail.
 */
trait LinagoraEmailSendExtraValidationContract {

  @Test
  def emailSendShouldBeRejectedWhenAnExtraValidationRejects(server: GuiceJamesServer): Unit = {
    val uniqueSuffix: String = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob: Username = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre: Username = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    provision(server, bob, andre)

    val response: String = `given`
      .body(bobSendsAMailToAndre(server, bob, andre))
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |  "K87": {
           |    "type": "invalidArguments",
           |    "description": "$DESCRIPTION"
           |  }
           |}""".stripMargin)
  }

  private def provision(server: GuiceJamesServer, bob: Username, andre: Username): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString, ANDRE_PASSWORD)

    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(bob))
    mailboxProbe.createMailbox(MailboxPath.inbox(andre))

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }
}
