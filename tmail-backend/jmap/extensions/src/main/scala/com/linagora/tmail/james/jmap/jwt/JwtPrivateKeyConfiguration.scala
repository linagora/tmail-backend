package com.linagora.tmail.james.jmap.jwt

import com.linagora.tmail.james.jmap.jwt.JwtPrivateKeyConfiguration.{DEFAULT_VALUE, fromPEM}
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.util.io.pem.PemReader
import org.slf4j.LoggerFactory

import java.io.{IOException, StringReader}
import java.security.PrivateKey

object JwtPrivateKeyConfiguration {
  private val LOGGER = LoggerFactory.getLogger(classOf[JwtPrivateKeyConfiguration])
  private val DEFAULT_VALUE = true

  def fromPEM(pemKey: Option[String]): Option[PrivateKey] =
    pemKey.map(key => new PEMParser(new PemReader(new StringReader(key))))
      .flatMap(reader => privateKeyFrom(reader))

  private def privateKeyFrom(reader: PEMParser): Option[PrivateKey] = {
    try {
      val readPEM = reader.readObject
      readPEM match {
        case privateKeyInfo: PrivateKeyInfo =>
          Option(new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo))
        case _ =>
          LOGGER.warn("Key is not an instance of SubjectPublicKeyInfo but of {}", readPEM)
          Option.empty
      }
    } catch {
      case e: IOException =>
        LOGGER.warn("Error when reading the PEM file", e)
        Option.empty
    }
  }
}

case class JwtPrivateKeyConfiguration(jwtPrivateKey: Option[String]) {
  require(validPrivateKey(jwtPrivateKey), "The provided private key is not valid")

  private def validPrivateKey(jwtPrivateKey: Option[String]): Boolean =
    jwtPrivateKey.map(value => fromPEM(Option(value)).isDefined)
      .getOrElse(DEFAULT_VALUE)
}
