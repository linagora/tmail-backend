package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.jwt.{JWTToken, JwtTokenResponse}
import play.api.libs.json.{JsString, JsValue, Json, Writes}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object JwtTokenSerializer {

  private implicit val tokenWrites: Writes[JWTToken] = Json.valueWrites[JWTToken]
  private implicit val expiresOnWrites: Writes[ZonedDateTime] = date => JsString(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")))
  private implicit val jwtTokenResponseWrites: Writes[JwtTokenResponse] = Json.writes[JwtTokenResponse]

  def serializeJwtTokenResponse(token: JwtTokenResponse): JsValue = Json.toJson(token)

}
