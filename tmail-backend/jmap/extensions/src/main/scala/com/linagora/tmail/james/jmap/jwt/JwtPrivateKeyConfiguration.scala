package com.linagora.tmail.james.jmap.jwt

import java.io.{IOException, StringReader}
import java.security.PrivateKey

import com.linagora.tmail.james.jmap.jwt.JwtPrivateKeyConfiguration.{DEFAULT_VALUE, fromPEM}
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.bouncycastle.util.io.pem.PemReader
import org.slf4j.LoggerFactory

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
          Some(new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo))
        case pemKeyPair: PEMKeyPair => Some(new JcaPEMKeyConverter().getPrivateKey(pemKeyPair.getPrivateKeyInfo))
        case privateKey: PrivateKey => Some(privateKey)
        case _ =>
          LOGGER.warn("Key is not an instance of PrivateKeyInfo but of {}", readPEM)
          None
      }
    } catch {
      case e: IOException =>
        LOGGER.warn("Error when reading the PEM file", e)
        None
    }
  }
}

case class JwtPrivateKeyConfiguration(jwtPrivateKey: Option[String]) {
  require(validPrivateKey(jwtPrivateKey), "The provided private key is not valid")

  private def validPrivateKey(jwtPrivateKey: Option[String]): Boolean =
    jwtPrivateKey.map(value => fromPEM(Option(value)).isDefined)
      .getOrElse(DEFAULT_VALUE)
}
