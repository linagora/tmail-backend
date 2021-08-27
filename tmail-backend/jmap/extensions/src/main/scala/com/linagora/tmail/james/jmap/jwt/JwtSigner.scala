package com.linagora.tmail.james.jmap.jwt

import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import org.apache.james.core.Username

import java.time.{Clock, ZonedDateTime}
import java.util.{Date, UUID}
import javax.inject.Inject

case class JWTToken(value: String) extends AnyVal

class JwtSigner @Inject() (clock: Clock, jwtPrivateKeyProvider: JwtPrivateKeyProvider) {
  def sign(user: Username, validUntil: ZonedDateTime): JWTToken =
    JWTToken(Jwts.builder()
      .setId(UUID.randomUUID().toString)
      .setSubject(user.asString())
      .signWith(SignatureAlgorithm.RS256, jwtPrivateKeyProvider.privateKey)
      .setIssuedAt(Date.from(clock.instant()))
      .setExpiration(Date.from(validUntil.toInstant))
      .compact())
}
