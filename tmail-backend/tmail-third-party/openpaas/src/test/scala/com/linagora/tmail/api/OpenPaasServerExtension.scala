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

package com.linagora.tmail.api

import java.net.URI

import com.linagora.tmail.HttpUtils
import com.linagora.tmail.api.OpenPaasServerExtension.{ALICE_EMAIL, ALICE_USER_ID, BAD_AUTHENTICATION_TOKEN, BOB_INVALID_EMAIL, BOB_USER_ID, ERROR_EMAIL, GOOD_AUTHENTICATION_TOKEN, GOOD_PASSWORD, GOOD_USER, LOGGER, NOTFOUND_EMAIL}
import org.junit.jupiter.api.extension._
import org.mockserver.configuration.ConfigurationProperties
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.NottableString.string
import org.slf4j.{Logger, LoggerFactory}

object OpenPaasServerExtension {
  val ALICE_USER_ID: String = "ALICE_USER_ID"
  val ALICE_EMAIL: String = "adoe@linagora.com"
  val NOTFOUND_EMAIL: String = "notfound@lina.com"
  val ERROR_EMAIL: String = "error@lina.com"
  val BOB_USER_ID: String = "BOB_USER_ID"
  val BOB_INVALID_EMAIL: String = "BOB_EMAIL_IS_INVALID"
  val GOOD_USER = "admin"
  val GOOD_PASSWORD = "admin"
  val BAD_USER = "BAD_USER"
  val BAD_PASSWORD = "BAD_PASSWORD"
  val GOOD_AUTHENTICATION_TOKEN: String = HttpUtils.createBasicAuthenticationToken(GOOD_USER, GOOD_PASSWORD)
  val BAD_AUTHENTICATION_TOKEN: String = HttpUtils.createBasicAuthenticationToken(BAD_USER, BAD_PASSWORD)
  private val LOGGER: Logger = LoggerFactory.getLogger(OpenPaasServerExtension.getClass)
}

class OpenPaasServerExtension extends BeforeEachCallback with AfterEachCallback with BeforeAllCallback with AfterAllCallback with ParameterResolver {
  var mockServer: ClientAndServer = _

  override def beforeAll(extensionContext: ExtensionContext): Unit = {
    mockServer = startClientAndServer(0)
    ConfigurationProperties.logLevel("DEBUG")
  }

  override def beforeEach(context: ExtensionContext): Unit = {
    mockServer.when(
      request.withPath(s"/users/$ALICE_USER_ID")
        .withMethod("GET")
        .withHeader(string("Authorization"), string(BAD_AUTHENTICATION_TOKEN)))
        .respond(response.withStatusCode(401))

    mockServer.when(
      request.withPath(s"/users/$ALICE_USER_ID")
        .withMethod("GET")
        .withHeader(string("Authorization"), string(GOOD_AUTHENTICATION_TOKEN)))
    .respond(response.withStatusCode(200)
    .withBody(s"""{
                |  "_id":  "$ALICE_USER_ID",
                |  "firstname": "Alice",
                |  "lastname": "DOE",
                |  "preferredEmail": "$ALICE_EMAIL",
                |  "emails": [
                |    "$ALICE_EMAIL"
                |  ],
                |  "domains": [
                |    {
                |      "joined_at": "2020-09-03T08:16:35.682Z",
                |      "domain_id": "$ALICE_USER_ID"
                |    }
                |  ],
                |  "states": [],
                |  "avatars": [
                |    "$ALICE_USER_ID"
                |  ],
                |  "main_phone": "01111111111",
                |  "accounts": [
                |    {
                |      "timestamps": {
                |        "creation": "2020-09-03T08:16:35.682Z"
                |      },
                |      "hosted": true,
                |      "emails": [
                |        "adoe@linagora.com"
                |      ],
                |      "preferredEmailIndex": 0,
                |      "type": "email"
                |    }
                |  ],
                |  "login": {
                |    "failures": [],
                |    "success": "2024-10-04T12:59:44.469Z"
                |  },
                |  "id": "$ALICE_USER_ID",
                |  "displayName": "Alice DOE",
                |  "objectType": "user",
                |  "followers": 0,
                |  "followings": 0
                |}""".stripMargin))

    mockServer.when(
        request.withPath(s"/users/$BOB_USER_ID")
          .withMethod("GET")
          .withHeader(string("Authorization"), string(GOOD_AUTHENTICATION_TOKEN)))
      .respond(response.withStatusCode(200)
        .withBody(s"""{
                     |  "_id":  "$BOB_USER_ID",
                     |  "firstname": "Alice",
                     |  "lastname": "DOE",
                     |  "preferredEmail": "$BOB_INVALID_EMAIL",
                     |  "emails": [
                     |    "$BOB_INVALID_EMAIL"
                     |  ],
                     |  "domains": [
                     |    {
                     |      "joined_at": "2020-09-03T08:16:35.682Z",
                     |      "domain_id": "$BOB_USER_ID"
                     |    }
                     |  ],
                     |  "states": [],
                     |  "avatars": [
                     |    "$BOB_USER_ID"
                     |  ],
                     |  "main_phone": "01111111111",
                     |  "accounts": [
                     |    {
                     |      "timestamps": {
                     |        "creation": "2020-09-03T08:16:35.682Z"
                     |      },
                     |      "hosted": true,
                     |      "emails": [
                     |        "adoe@linagora.com"
                     |      ],
                     |      "preferredEmailIndex": 0,
                     |      "type": "email"
                     |    }
                     |  ],
                     |  "login": {
                     |    "failures": [],
                     |    "success": "2024-10-04T12:59:44.469Z"
                     |  },
                     |  "id": "$BOB_USER_ID",
                     |  "displayName": "Alice DOE",
                     |  "objectType": "user",
                     |  "followers": 0,
                     |  "followings": 0
                     |}""".stripMargin))

    setSearchEmailExist(ALICE_EMAIL, ALICE_USER_ID)
    setSearchEmailNotFound(NOTFOUND_EMAIL)

    mockServer.when(
        request.withPath(s"/users")
          .withQueryStringParameter("email", ERROR_EMAIL)
          .withMethod("GET")
          .withHeader(string("Authorization"), string(GOOD_AUTHENTICATION_TOKEN)))
      .respond(response.withStatusCode(503)
        .withBody(s"""503 Temporary error""".stripMargin))
  }

  override def afterEach(context: ExtensionContext): Unit = {
    if (mockServer == null) {
      LOGGER.warn("Mock server is null")
    } else {
      mockServer.reset()
    }
  }

  override def afterAll(extensionContext: ExtensionContext): Unit = {
    if (mockServer == null) {
      LOGGER.warn("Mock server is null")
    } else {
      mockServer.close()
    }
  }

  override def supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.getParameter.getType eq classOf[ClientAndServer]

  override def resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): AnyRef =
    mockServer

  def getBaseUrl: URI = new URI(s"http://localhost:${mockServer.getLocalPort}")

  def getUsername: String = GOOD_USER

  def getPassword: String = GOOD_PASSWORD

  def setSearchEmailExist(emailQuery: String, openPassUidExpected: String): Unit = {
    mockServer.when(
      request.withPath(s"/users")
        .withQueryStringParameter("email", emailQuery)
        .withMethod("GET")
        .withHeader(string("Authorization"), string(GOOD_AUTHENTICATION_TOKEN)))
      .respond(response.withStatusCode(200)
        .withBody(s"""[
                     |    {
                     |        "_id": "$openPassUidExpected",
                     |        "firstname": "John1",
                     |        "lastname": "Doe1",
                     |        "preferredEmail": "$emailQuery",
                     |        "emails": [
                     |            "$emailQuery"
                     |        ],
                     |        "domains": [
                     |            {
                     |                "joined_at": "2024-12-17T13:00:22.766Z",
                     |                "domain_id": "676175e3aea7130059d339b2"
                     |            }
                     |        ],
                     |        "states": [],
                     |        "avatars": [],
                     |        "id": "$openPassUidExpected",
                     |        "displayName": "John1 Doe1",
                     |        "objectType": "user"
                     |    }
                     |]""".stripMargin))
  }

  def setSearchEmailNotFound(emailQuery: String): Unit = {
    mockServer.when(
      request.withPath(s"/users")
        .withQueryStringParameter("email", emailQuery)
        .withMethod("GET")
        .withHeader(string("Authorization"), string(GOOD_AUTHENTICATION_TOKEN)))
      .respond(response.withStatusCode(200)
        .withBody(s"""[]""".stripMargin))
  }


}
