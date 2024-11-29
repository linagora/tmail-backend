package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.service.discovery.{LinagoraServicesDiscoveryConfiguration, LinagoraServicesDiscoveryItem, ServicesDiscoveryConfigurationSerializers => testee}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.Json

class LinagoraServicesDiscoveryConfigurationSerializeTest {

  @Test
  def serializeShouldHandleFlatPropertiesCorrectly(): Unit = {
    val input = LinagoraServicesDiscoveryConfiguration(services = List(
      LinagoraServicesDiscoveryItem("linShareApiUrl", "https://linshare.linagora.com/linshare/webservice"),
      LinagoraServicesDiscoveryItem("linToApiUrl", "https://linto.ai/demo"),
      LinagoraServicesDiscoveryItem("linToApiKey", "apiKey")))

    assertThat(Json.parse(testee.serialize(input)))
      .isEqualTo(Json.parse(
        """{
          |    "linShareApiUrl" : "https://linshare.linagora.com/linshare/webservice",
          |    "linToApiUrl" : "https://linto.ai/demo",
          |    "linToApiKey" : "apiKey"
          |  }""".stripMargin))
  }

  @Test
  def serializeShouldHandleNestedPropertiesCorrectly(): Unit = {
    val input = LinagoraServicesDiscoveryConfiguration(services = List(
      LinagoraServicesDiscoveryItem("mobileApps.Lin1.url1", "url1"),
      LinagoraServicesDiscoveryItem("mobileApps.Lin1.url2", "url2"),
      LinagoraServicesDiscoveryItem("mobileApps.Lin2.url3", "url3"),
      LinagoraServicesDiscoveryItem("mobileApps.Lin4", "url4")))

    assertThat(Json.parse(testee.serialize(input)))
      .isEqualTo(Json.parse(
        """{
          |    "mobileApps": {
          |        "Lin1": {
          |            "url1": "url1",
          |            "url2": "url2"
          |        },
          |        "Lin2": {
          |            "url3": "url3"
          |        },
          |        "Lin4": "url4"
          |    }
          |}""".stripMargin))
  }

  @Test
  def serializeShouldTransformUnderscoreToSpaceInNestedKeys(): Unit = {
    val input = LinagoraServicesDiscoveryConfiguration(services = List(
      LinagoraServicesDiscoveryItem("linToApiKey", "apiKey"),
      LinagoraServicesDiscoveryItem("mobileApps.Twake_Chat.logoURL", "https://xyz"),
      LinagoraServicesDiscoveryItem("mobileApps.Twake_Chat.appId", "abc"),
      LinagoraServicesDiscoveryItem("mobileApps.Twake_Drive.logoURL", "https://xyz"),
      LinagoraServicesDiscoveryItem("mobileApps.Twake_Drive.webLink", "https://tdrive.linagora.com"),
      LinagoraServicesDiscoveryItem("mobileApps.TwakeXyz.Logo_URL", "https://xyz")
    ))

    assertThat(Json.parse(testee.serialize(input)))
      .isEqualTo(Json.parse(
        """{
          |    "mobileApps": {
          |        "TwakeXyz": {
          |            "Logo URL": "https://xyz"
          |        },
          |        "Twake Chat": {
          |            "logoURL": "https://xyz",
          |            "appId": "abc"
          |        },
          |        "Twake Drive": {
          |            "logoURL": "https://xyz",
          |            "webLink": "https://tdrive.linagora.com"
          |        }
          |    },
          |    "linToApiKey": "apiKey"
          |}""".stripMargin))
  }

  @Test
  def serilizeShouldSuccessWhenEmptyConfiguration(): Unit = {
    val input = LinagoraServicesDiscoveryConfiguration(services = List())

    assertThat(Json.parse(testee.serialize(input)))
      .isEqualTo(Json.parse("{}"))
  }
}
