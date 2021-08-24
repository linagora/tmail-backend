package com.linagora.tmail.james.jmap.jwt

import com.linagora.tmail.james.jmap.jwt.Fixture.validPrivateKey
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

class JwtPrivateKeyConfigurationTest {
  @Test
  def getJwtPrivateKeyPemShouldReturnEmptyWhenEmptyPrivateKey(): Unit = {
    val jwtPrivateKeyConfiguration = new JwtPrivateKeyConfiguration(Option.empty)

    assertThat(jwtPrivateKeyConfiguration.jwtPrivateKey).isEqualTo(Option.empty)
  }

  @Test
  def contructorShouldThrowWhenNullPrivateKey(): Unit = {
    assertThatThrownBy(() => new JwtPrivateKeyConfiguration(null))
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def constructorShouldThrowWhenNonePrivateKey(): Unit = {
    assertThatThrownBy(() => new JwtPrivateKeyConfiguration(Option("")))
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def constructorShouldThrowWhenInvalidPrivateKey(): Unit = {
    assertThatThrownBy(() => new JwtPrivateKeyConfiguration(Option("invalid_key")))
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def getJwtPrivateKeyPemShouldReturnWhenValidPrivateKey(): Unit = {
    val jwtPrivateKeyConfiguration = new JwtPrivateKeyConfiguration(Option(validPrivateKey))

    assertThat(jwtPrivateKeyConfiguration.jwtPrivateKey).isEqualTo(Option(validPrivateKey))
  }
}
