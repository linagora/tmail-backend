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
  def getShouldNotThrowWhenPEMKeyProvided(): Unit = {
    val jwtPrivateKeyConfiguration = new JwtPrivateKeyConfiguration(Option(validPrivateKey))

    val privateKeyProvider = new JwtPrivateKeyProvider(jwtPrivateKeyConfiguration)

    assertThat(privateKeyProvider.get()).isInstanceOf(classOf[RSAPrivateKey])
  }

  @Test
  def getShouldThrowWhenPEMKeyNotProvided(): Unit = {
    val jwtPrivateKeyConfiguration = new JwtPrivateKeyConfiguration(Option.empty)

    val privateKeyProvider = new JwtPrivateKeyProvider(jwtPrivateKeyConfiguration)

    assertThatThrownBy(() => privateKeyProvider.get()).isExactlyInstanceOf(classOf[MissingOrInvalidKeyException])
  }
}
