package com.linagora.tmail.james.jmap.jwt

import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import org.apache.james.core.Username

import java.time.ZonedDateTime
import java.util.{Date, UUID}

case class JWTToken(value: String) extends AnyVal

class JwtSigner(jwtPrivateKeyProvider: JwtPrivateKeyProvider) {
  def sign(user: Username, validUntil: ZonedDateTime): JWTToken =
    JWTToken(Jwts.builder()
      .setId(UUID.randomUUID().toString)
      .setSubject(user.asString())
      .signWith(SignatureAlgorithm.RS256, jwtPrivateKeyProvider.get())
      .setIssuedAt(Date.from(ZonedDateTime.now().toInstant))
      .setExpiration(Date.from(validUntil.toInstant))
      .compact())
}
