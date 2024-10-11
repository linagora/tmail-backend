package com.linagora.tmail.api

import com.linagora.tmail.HttpUtils
import com.linagora.tmail.api.OpenPaasServerExtension.{ALICE_EMAIL, ALICE_USER_ID, BAD_AUTHENTICATION_TOKEN, GOOD_AUTHENTICATION_TOKEN}
import org.junit.jupiter.api.extension._
import org.mockserver.configuration.ConfigurationProperties
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.NottableString.string

import java.net.{URI, URL}

object OpenPaasServerExtension {
  val ALICE_USER_ID: String = "abc0a663bdaffe0026290xyz"
  val ALICE_EMAIL: String = "adoe@linagora.com"
  val GOOD_USER = "admin"
  val GOOD_PASSWORD = "admin"
  val BAD_USER = "BAD_USER"
  val BAD_PASSWORD = "BAD_PASSWORD"
  val GOOD_AUTHENTICATION_TOKEN: String = HttpUtils.createBasicAuthenticationToken(GOOD_USER, GOOD_PASSWORD)
  val BAD_AUTHENTICATION_TOKEN: String = HttpUtils.createBasicAuthenticationToken(BAD_USER, BAD_PASSWORD)
}

class OpenPaasServerExtension extends BeforeEachCallback with AfterEachCallback with ParameterResolver{

  var mockServer: ClientAndServer = _

  override def beforeEach(context: ExtensionContext): Unit = {
    mockServer = startClientAndServer(0)
    ConfigurationProperties.logLevel("DEBUG")

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
  }

  override def afterEach(context: ExtensionContext): Unit = mockServer.close()

  override def supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.getParameter.getType eq classOf[ClientAndServer]

  override def resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): AnyRef =
    mockServer

  def getBaseUrl: URL = new URI(s"http://localhost:${mockServer.getLocalPort}").toURL

}
