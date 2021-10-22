package com.linagora.tmail.james.jmap.jwt

import java.security.Security
import java.time.ZonedDateTime
import java.util.Optional

import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.jmap.jwt.Fixture.{validPrivateKey, validPublicKey}
import com.linagora.tmail.james.jmap.jwt.JwtSignerTest.{bob, differentValidPublicKey, expiredExpirationTime, now, validExpirationTime}
import org.apache.james.core.Username
import org.apache.james.jwt.{JwtConfiguration, JwtTokenVerifier, PublicKeyProvider, PublicKeyReader}
import org.apache.james.utils.UpdatableTickingClock
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.{BeforeAll, BeforeEach, Test}

object JwtSignerTest {
  private val bob = Username.of("bob")
  private val now: ZonedDateTime = ZonedDateTime.now()
  private val validExpirationTime = now.plusHours(1)
  private val expiredExpirationTime = now.minusHours(1)

  private val differentValidPublicKey =
    """
      |-----BEGIN PUBLIC KEY-----
      |MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtlChO/nlVP27MpdkG0Bh
      |16XrMRf6M4NeyGa7j5+1UKm42IKUf3lM28oe82MqIIRyvskPc11NuzSor8HmvH8H
      |lhDs5DyJtx2qp35AT0zCqfwlaDnlDc/QDlZv1CoRZGpQk1Inyh6SbZwYpxxwh0fi
      |+d/4RpE3LBVo8wgOaXPylOlHxsDizfkL8QwXItyakBfMO6jWQRrj7/9WDhGf4Hi+
      |GQur1tPGZDl9mvCoRHjFrD5M/yypIPlfMGWFVEvV5jClNMLAQ9bYFuOc7H1fEWw6
      |U1LZUUbJW9/CH45YXz82CYqkrfbnQxqRb2iVbVjs/sHopHd1NTiCfUtwvcYJiBVj
      |kwIDAQAB
      |-----END PUBLIC KEY-----""".stripMargin

  @BeforeAll
  def init(): Unit = {
    Security.addProvider(new BouncyCastleProvider)
  }
}

class JwtSignerTest {
  private var jwtSigner: JwtSigner = null
  private var jwtTokenVerifier: JwtTokenVerifier = null

  @BeforeEach
  def setup(): Unit = {
    val privateKeyProvider = new JwtPrivateKeyProvider(new JwtPrivateKeyConfiguration(Option(validPrivateKey)))
    val clock = new UpdatableTickingClock(now.toInstant)
    jwtSigner = new JwtSigner(clock, privateKeyProvider)

    val publicKeyProvider = new PublicKeyProvider(new JwtConfiguration(ImmutableList.of(validPublicKey)), new PublicKeyReader)
    jwtTokenVerifier = new JwtTokenVerifier(publicKeyProvider)
  }

  @Test
  def verifyShouldReturnTrueOnValidSignedToken(): Unit = {
    val jwtToken = jwtSigner.sign(bob, validExpirationTime)

    assertThat(jwtTokenVerifier.verifyAndExtractLogin(jwtToken.value))
      .isNotEmpty
  }

  @Test
  def verifyShouldReturnUserLoginFromValidSignedToken(): Unit = {
    val jwtToken = jwtSigner.sign(bob, validExpirationTime)

    assertThat(jwtTokenVerifier.verifyAndExtractLogin(jwtToken.value))
      .isEqualTo(Optional.of(bob.asString()))
  }

  @Test
  def verifyShouldReturnFalseOnExpiredSignedToken(): Unit = {
    val jwtToken = jwtSigner.sign(bob, expiredExpirationTime)

    assertThat(jwtTokenVerifier.verifyAndExtractLogin(jwtToken.value))
      .isEmpty
  }

  @Test
  def signShouldThrowOnNullExpiredTime(): Unit = {
    assertThatThrownBy(() => jwtSigner.sign(bob, null))
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def signShouldThrowOnNullUsername(): Unit = {
    assertThatThrownBy(() => jwtSigner.sign(null, expiredExpirationTime))
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def signShouldThrowOnEmptyUsername(): Unit = {
    assertThatThrownBy(() => jwtSigner.sign(Username.of(""), validExpirationTime))
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def verifyShouldReturnFalseWhenKeysAreNotPaired(): Unit = {
    val otherPublicKeyProvider = new PublicKeyProvider(new JwtConfiguration(ImmutableList.of(differentValidPublicKey)), new PublicKeyReader)
    val otherJwtTokenVerifier = new JwtTokenVerifier(otherPublicKeyProvider)

    val jwtToken = jwtSigner.sign(bob, validExpirationTime)

    assertThat(otherJwtTokenVerifier.verifyAndExtractLogin(jwtToken.value))
      .isEmpty
  }
}
