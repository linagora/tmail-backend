package com.linagora.tmail.james.jmap.longlivedtoken

import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.jmap.longlivedtoken.LongLivedTokenAuthenticationStrategyTest.{AUTHORIZATION_HEADERS, SECRET_UUID, TOKEN, USER_NAME}
import io.netty.handler.codec.http.HttpHeaders
import org.apache.james.core.Username
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.{BeforeEach, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{mock, when}
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.HttpServerRequest

object LongLivedTokenAuthenticationStrategyTest {
  val AUTHORIZATION_HEADERS: String = "Authorization"
  val USER_NAME: String = "bob@domain"
  val SECRET_UUID: LongLivedTokenSecret = LongLivedTokenSecret.parse("89b8a916-463d-49b8-956e-80e2a64398d8")
    .toOption.get
  val TOKEN: String = USER_NAME + "_" + SECRET_UUID.value.toString
}

class LongLivedTokenAuthenticationStrategyTest {

  var testee: LongLivedTokenAuthenticationStrategy = _
  var mockedRequest: HttpServerRequest = _
  var mockedHeaders: HttpHeaders = _
  var mockedMailboxManager: MailboxManager = _
  var longLivedTokenStore: LongLivedTokenStore = _

  @BeforeEach
  def beforeEach(): Unit = {
    mockedRequest = mock(classOf[HttpServerRequest])
    mockedHeaders = mock(classOf[HttpHeaders])

    mockedMailboxManager = mock(classOf[MailboxManager])
    longLivedTokenStore = mock(classOf[LongLivedTokenStore])
    when(mockedRequest.requestHeaders).thenReturn(mockedHeaders)

    testee = new LongLivedTokenAuthenticationStrategy(longLivedTokenStore, mockedMailboxManager)
  }

  @Test
  def createMailboxSessionShouldReturnEmptyWhenAuthHeaderIsEmpty(): Unit = {
    when(mockedHeaders.get(AUTHORIZATION_HEADERS))
      .thenReturn("")
    assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
      .isEmpty()
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    "Bearer abc",
    "Bearer broken_",
    "Bearerbroken_",
    "broken_",
    "Basic broken_",
    "_",
    "_ ",
    " _",
  ))
  def createMailboxSessionShouldReturnEmptyWhenAuthHeadersIsInvalid(valueToken: String): Unit = {
    when(mockedHeaders.get(AUTHORIZATION_HEADERS))
      .thenReturn(valueToken)
    assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
      .isEmpty()
  }

  @Test
  def createMailboxSessionShouldThrowWhenMultipleHeaders(): Unit = {
    when(mockedHeaders.getAll(AUTHORIZATION_HEADERS))
      .thenReturn(ImmutableList.of("Bearer abc", "Bearer xyz"))
    assertThatThrownBy(() => testee.createMailboxSession(mockedRequest).blockOptional)
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def createMailboxSessionShouldThrowWhenSecretUUIDInValid(): Unit = {
    when(mockedHeaders.get(AUTHORIZATION_HEADERS))
      .thenReturn("Bearer " + TOKEN)
    when(longLivedTokenStore.validate(ArgumentMatchers.eq(Username.of(USER_NAME)), ArgumentMatchers.eq(SECRET_UUID)))
      .thenReturn(SMono.empty)

    assertThatCode(() => testee.createMailboxSession(mockedRequest).block())
      .isInstanceOf(classOf[UnauthorizedException])
  }

  @Test
  def createMailboxSessionShouldReturnValidSessionWhenAuthHeadersAreValid(): Unit = {
    when(mockedHeaders.get(AUTHORIZATION_HEADERS))
      .thenReturn("Bearer " + TOKEN)
    val fakeMailboxSession: MailboxSession = mock(classOf[MailboxSession])
    when(mockedMailboxManager.createSystemSession(ArgumentMatchers.eq(Username.of(USER_NAME))))
      .thenReturn(fakeMailboxSession)
    when(longLivedTokenStore.validate(ArgumentMatchers.eq(Username.of(USER_NAME)), ArgumentMatchers.eq(SECRET_UUID)))
      .thenReturn(SMono.just(LongLivedTokenFootPrint(LongLivedTokenId.generate, DeviceId("deviceId1"))))

    assertThat(testee.createMailboxSession(mockedRequest).block())
      .isEqualTo(fakeMailboxSession)
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    "bob_bob@linagora",
    "_bob",
    "_bob_",
    "_bob__",
    "bob__@a"
  ))
  def createMailboxSessionShouldReturnValidSessionWhenAuthHeaderHasSeveralSpecialCharacter(username: String): Unit = {
    val USER_NAME: Username = Username.of("bob_bob@linagora")

    when(mockedHeaders.get(AUTHORIZATION_HEADERS))
      .thenReturn("Bearer " + USER_NAME.asString() + "_" + SECRET_UUID.value.toString)
    val fakeMailboxSession: MailboxSession = mock(classOf[MailboxSession])
    when(mockedMailboxManager.createSystemSession(ArgumentMatchers.eq(USER_NAME)))
      .thenReturn(fakeMailboxSession)
    when(longLivedTokenStore.validate(ArgumentMatchers.eq(USER_NAME), ArgumentMatchers.eq(SECRET_UUID)))
      .thenReturn(SMono.just(LongLivedTokenFootPrint(LongLivedTokenId.generate, DeviceId("deviceId1"))))

    assertThat(testee.createMailboxSession(mockedRequest).block())
      .isEqualTo(fakeMailboxSession)
  }

}
