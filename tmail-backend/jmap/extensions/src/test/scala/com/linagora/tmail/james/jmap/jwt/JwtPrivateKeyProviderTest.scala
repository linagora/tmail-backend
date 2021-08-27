package com.linagora.tmail.james.jmap.jwt

import com.linagora.tmail.james.jmap.jwt.Fixture.validPrivateKey
import org.apache.james.jwt.MissingOrInvalidKeyException
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.{BeforeAll, Test}

import java.security.Security
import java.security.interfaces.RSAPrivateKey

object JwtPrivateKeyProviderTest {
  @BeforeAll
  def init(): Unit = {
    Security.addProvider(new BouncyCastleProvider)
  }
}

class JwtPrivateKeyProviderTest {
  @Test
  def providerShouldNotThrowWhenPEMKeyProvided(): Unit = {
    val jwtPrivateKeyConfiguration = new JwtPrivateKeyConfiguration(Option(validPrivateKey))

    val privateKeyProvider = new JwtPrivateKeyProvider(jwtPrivateKeyConfiguration)

    assertThat(privateKeyProvider.privateKey).isInstanceOf(classOf[RSAPrivateKey])
  }

  @Test
  def providerShouldThrowWhenPEMKeyNotProvided(): Unit = {
    val jwtPrivateKeyConfiguration = new JwtPrivateKeyConfiguration(None)

    assertThatThrownBy(() => new JwtPrivateKeyProvider(jwtPrivateKeyConfiguration)).isExactlyInstanceOf(classOf[MissingOrInvalidKeyException])
  }
}
