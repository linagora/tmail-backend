package com.linagora.tmail.james.jmap.jwt

import com.linagora.tmail.james.jmap.jwt.Fixture.validPrivateKey
import org.apache.james.jwt.MissingOrInvalidKeyException
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.{BeforeAll, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}

import java.security.Security
import java.security.interfaces.RSAPrivateKey
import java.util.stream.Stream

object JwtPrivateKeyProviderTest {
  /**
   * Private key generation used for this example:
   * ``
   * $ openssl genrsa -des3 -out private.key 2048
   * $ openssl rsa -in private.key -out jwt_privatekey
   * ``
   */
  private val validPrivateKey2: String =
    """
      |-----BEGIN RSA PRIVATE KEY-----
      |MIIEpAIBAAKCAQEAnFWLGA4GN9a55ZpnVjS+PbCOvISwimphJnhepXZovkch4Lg4
      |lVoeJHwpzPTr4o/jXviHKKY5zoJAroZBlFCz5TuEw0VZBUZqyzQkDDIRTDo9sf0c
      |tiCf0NRhVjCla1gzaQCJHVTOGt31QjLhcLXC34TraxEMRcB1i3dE0iMASC+fTN0X
      |ScXVFwxPp6R9QWDyHBFf2HDzpwM4lSg3gp46KwQi69PzldU1wKrN4NU99FVB84+/
      |E0azJ9OyuCnCDz3UHRQlcTF3jH4WhYYne6Rd2sFiN8LiNiyFmgZcVz1FRRr+FzF1
      |is63wCgC1nqGuNSuMM7WYlAHMJCiAXS8WPQzoQIDAQABAoIBAQCO8TyMEw6mccRp
      |5sMDtJgZ6dypDJ4rAVexCDBqFBlzmbClO2wpS0vySkEiMPOZpbzc8lsK1OpYIwqn
      |SQSfaycBu2kJ7teVlixBfnxTVlgwnbQZkXh2IuBd3kLdvv8RZoUjRiUY15jYQghl
      |rgYpu1fibjOfIuhYwr/3dGdNyEkStmKVy4gkf7Fvb+j6Nz4bQ7mM5+4Fe3D60sVT
      |SWDKYHTk++23jgXwsklB/IM7yHUgbOe0D8ilqO4WU3OinLnrnwXuVns9S16dFXrL
      |D4Y7vnpipMqLKPbtITlLj5Pvthdq28YZ2SjLAjxn51DTz/mO7T1Fjhr9NrKEjl0k
      |WGqrMgINAoGBAMld2tLDvZs1m6BjV7Z169UajTuqL0lPb3xbPVNepRahS264gTRK
      |fpKmo7fnDFcldpjoTCWuao44exQAoP3RvTrVydEnfO+6dO9AP4pP2CcBbg9u3cUK
      |jJtICqpKJzrZKCpPyxWpMZgBfqR14jmatCWq9tOVYXn7aU6leqzdlGB/AoGBAMa/
      |6Gz7DjhfO20e3cwr1Wdw76EAqYLFttN4g6vj4rjA892sJR5a5qFOY1vrB7Pb4wnq
      |Rb68JWH//PhAmseqPvEmmR0fYw+hUNYY83AvKCeVgjWcRkQ68r8G9vxIYicb/6x9
      |VAklvMHrUubJWDeourJ9MOqTeQe6PpApaMTWGVvfAoGAKK18Ae2mxM7chFbtJh/G
      |J0N42l+zs7SHSfDIf6nz4ZFtwo0lFKIj1Y4yLnlPJd+ciKEMmOQWBGrmehDydLPE
      |7Ti4zzaR53+cFaS98TvV53NDf3ye8ioCY2/3L5VRPXMWyQ1bciG+sf1DTwe9TnOx
      |Bpb1y6I2kcS27jtBf+A3FBMCgYEAi61PQVfnFIMJFpU8t0KPBdV+8x2uA7PD2za1
      |AtZy/fjM6hsTMxQbRX37ohu5HBQKqWs1fOhpNUhclnOA05W4Cm5f6PGoAtISJ4T9
      |gPgDNl6CVf7v+v+andndNkwAfw/UTXr+1jbpQzeI2ZzjHKq+GraU7CacRmwDj/kq
      |Ijt9Mu0CgYBEqw0+qEqwsJVznEWllimshZKT1+2A/APHD9vTuif9WazmiMPS2zJJ
      |Whck0lvmETfo7JMcNMDxZJ51DgrM4FiLm8k+XNVCTkojXqvmT0KbDTXZOgZxVVyX
      |7svxCKa7ZcdZccLC20FiIT6zV3tTOj7g9+oDL3NmR0QYN0QEYwz+kg==
      |-----END RSA PRIVATE KEY-----""".stripMargin

  /**
   * Private key generation used for this example:
   * ``
   * $ openssl genrsa -out jwt_privatekey 4096
   * ``
   */
  private val validPrivateKey3: String =
    """"
      |-----BEGIN RSA PRIVATE KEY-----
      |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
      |-----END RSA PRIVATE KEY-----""".stripMargin

  def privateKeyStream : Stream[Arguments] = {
    Stream.of(
      Arguments.of(validPrivateKey),
      Arguments.of(validPrivateKey2),
      Arguments.of(validPrivateKey3))
  }

  @BeforeAll
  def init(): Unit = {
    Security.addProvider(new BouncyCastleProvider)
  }
}

class JwtPrivateKeyProviderTest {
  @ParameterizedTest
  @MethodSource(value = Array("privateKeyStream"))
  def providerShouldNotThrowWhenPEMKeyProvided(privateKey: String): Unit = {
    val jwtPrivateKeyConfiguration = new JwtPrivateKeyConfiguration(Some(privateKey))

    val privateKeyProvider = new JwtPrivateKeyProvider(jwtPrivateKeyConfiguration)

    assertThat(privateKeyProvider.privateKey).isInstanceOf(classOf[RSAPrivateKey])
  }

  @Test
  def providerShouldThrowWhenPEMKeyNotProvided(): Unit = {
    val jwtPrivateKeyConfiguration = new JwtPrivateKeyConfiguration(None)

    assertThatThrownBy(() => new JwtPrivateKeyProvider(jwtPrivateKeyConfiguration)).isExactlyInstanceOf(classOf[MissingOrInvalidKeyException])
  }
}
