package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.jwt.{JWTToken, JwtTokenResponse}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.Json

import java.time.{Instant, ZoneId, ZonedDateTime}

object JwtTokenSerializerTest {
  lazy val NOW: Instant = Instant.parse("2018-11-13T12:33:55Z")
}

class JwtTokenSerializerTest {

  @Test
  def serializeJwtTokenResponseShouldWork(): Unit = {
    val jwtTokenResponse: JwtTokenResponse = JwtTokenResponse(JWTToken("jwt 123"), ZonedDateTime.ofInstant(JwtTokenSerializerTest.NOW, ZoneId.of("Europe/Paris")))
    assertThat(JwtTokenSerializer.serializeJwtTokenResponse(jwtTokenResponse))
      .isEqualTo(Json.parse(
        """
          |{
          |  "token" : "jwt 123",
          |  "expiresOn" : "2018-11-13T13:33:55.000+01:00"
          |}""".stripMargin))
  }
}
