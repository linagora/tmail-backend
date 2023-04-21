package com.linagora.tmail.james.jmap.apisix

import io.netty.handler.codec.http.HttpHeaders
import org.apache.commons.lang3.NotImplementedException
import org.apache.james.core.{Domain, Username}
import org.apache.james.dnsservice.api.DNSService
import org.apache.james.domainlist.lib.DomainListConfiguration
import org.apache.james.domainlist.memory.MemoryDomainList
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.mailbox.exception.MailboxException
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SessionProvider}
import org.apache.james.user.memory.MemoryUsersRepository
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.mockito.invocation.InvocationOnMock
import reactor.netty.http.server.HttpServerRequest

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.function.Predicate

class ApisixXUserinfoAuthenticationStrategyTest {

  var testee: ApisixXUserinfoAuthenticationStrategy = _
  var mockedRequest: HttpServerRequest = _
  var mockedHeaders: HttpHeaders = _

  @BeforeEach
  def setUp(): Unit = {
    val mockedMailboxManager = mock(classOf[MailboxManager])
    mockedRequest = mock(classOf[HttpServerRequest])
    mockedHeaders = mock(classOf[HttpHeaders])
    val dnsService = mock(classOf[DNSService])
    val domainList = new MemoryDomainList(dnsService)
    domainList.configure(DomainListConfiguration.DEFAULT)
    domainList.addDomain(Domain.of("tmail.com"))
    val usersRepository = MemoryUsersRepository.withVirtualHosting(domainList)

    val fakeMailboxSession = mock(classOf[MailboxSession])
    when(mockedMailboxManager.createSystemSession(any)).thenReturn(fakeMailboxSession)
    when(mockedMailboxManager.authenticate(any))
      .thenAnswer((invocationOnMock: InvocationOnMock) => {
        new SessionProvider.AuthorizationStep() {
          override def as(other: Username) = throw new NotImplementedException

          override def withoutDelegation: MailboxSession = {
            when(fakeMailboxSession.getUser).thenReturn(invocationOnMock.getArguments.head.asInstanceOf[Username])
            fakeMailboxSession
          }

          @throws[MailboxException]
          override def forMatchingUser(other: Predicate[Username]) = throw new NotImplementedException
        }
      })

    when(mockedRequest.requestHeaders).thenReturn(mockedHeaders)
    testee = new ApisixXUserinfoAuthenticationStrategy(usersRepository, mockedMailboxManager)
  }

  @Test
  def createMailboxSessionShouldReturnEmptyWhenHeaderIsEmpty(): Unit = {
    when(mockedHeaders.get("X-Userinfo")).thenReturn("")
    assertThat(testee.createMailboxSession(mockedRequest).blockOptional).isEmpty
  }

  @Test
  def createMailboxSessionShouldReturnEmptyWhenHeaderIsNull(): Unit = {
    when(mockedHeaders.get("X-Userinfo")).thenReturn(null)
    assertThat(testee.createMailboxSession(mockedRequest).blockOptional).isEmpty
  }

  @Test
  def createMailboxSessionShouldFailWhenInvalidValue(): Unit = {
    when(mockedHeaders.get("X-Userinfo")).thenReturn("abcxyz") // invalid because virtual hosting is turned on

    assertThatThrownBy(() => testee.createMailboxSession(mockedRequest).blockOptional)
      .isInstanceOf(classOf[UnauthorizedException])
  }

  @Test
  def createMailboxSessionShouldReturnSessionWhenValid(): Unit = {
    val headerValue: String = Base64.getEncoder
      .encodeToString("{\"sub\":\"james-user@tmail.com\",\"name\":\"james-user\"}".getBytes(StandardCharsets.UTF_8))
    when(mockedHeaders.get("X-Userinfo")).thenReturn(headerValue)
    val mailboxSession = testee.createMailboxSession(mockedRequest).blockOptional

    assertThat(mailboxSession).isPresent
    assertThat(mailboxSession.get().getUser)
      .isEqualTo(Username.of("james-user@tmail.com"))
  }
}
