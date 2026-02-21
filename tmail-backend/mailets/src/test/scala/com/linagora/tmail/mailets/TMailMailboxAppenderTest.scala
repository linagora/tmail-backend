/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.mailets

import java.time.Duration

import com.linagora.tmail.mailets.TMailMailboxAppenderTest.{DOMAIN, EMPTY_FOLDER, FOLDER, STORAGE_DIRECTIVE, TEAM_MAILBOX, USER}
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxCallbackNoop, TeamMailboxName, TeamMailboxRepository, TeamMailboxRepositoryImpl}
import eu.timepit.refined.auto._
import jakarta.mail.MessagingException
import jakarta.mail.internet.MimeMessage
import org.apache.james.core.builder.MimeMessageBuilder
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.{FetchGroup, MailboxPath, MessageRange}
import org.apache.james.mailbox.store.StoreSubscriptionManager
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.util.concurrency.ConcurrentTestRunner
import org.apache.mailet.StorageDirective
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.{BeforeEach, RepeatedTest, Test}
import reactor.core.scala.publisher.SMono

object TMailMailboxAppenderTest {
  private val TEAM_MAILBOX_NAME: TeamMailboxName = TeamMailboxName("james")
  private val DOMAIN: Domain = Domain.of("linagora.com")
  private val USER: Username = Username.fromLocalPartWithDomain(TEAM_MAILBOX_NAME.value, DOMAIN)
  private val TEAM_MAILBOX: TeamMailbox = TeamMailbox(USER.getDomainPart.get, TEAM_MAILBOX_NAME)
  private val FOLDER = "folder"
  private val STORAGE_DIRECTIVE = StorageDirective.builder()
    .targetFolder(FOLDER)
    .build()
  private val EMPTY_FOLDER: String = ""
}

class TMailMailboxAppenderTest {
  private var testee: TMailMailboxAppender = null
  private var mailboxManager: MailboxManager = null
  private var mimeMessage: MimeMessage = null
  private var tmSession: MailboxSession = null
  private var userSession: MailboxSession = null
  private var teamMailboxRepository: TeamMailboxRepository = null

  @BeforeEach
  def setup(): Unit = {
    mimeMessage = MimeMessageBuilder.mimeMessageBuilder
      .setMultipartWithBodyParts(
        MimeMessageBuilder.bodyPartBuilder
          .data("toto"))
      .build

    val resources = InMemoryIntegrationResources.defaultResources()
    mailboxManager = resources.getMailboxManager
    val subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager.getMapperFactory,
      resources.getMailboxManager.getMapperFactory, resources.getMailboxManager.getEventBus)

    teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, subscriptionManager, resources.getMailboxManager.getMapperFactory, TeamMailboxCallbackNoop.asSet)

    testee = new TMailMailboxAppender(teamMailboxRepository, mailboxManager)

    tmSession = mailboxManager.createSystemSession(Username.fromLocalPartWithDomain("team-mailbox", DOMAIN))
    userSession = mailboxManager.createSystemSession(USER)
  }

  @Test
  def appendShouldAddMessageToDesiredTeamMailbox(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block()
    testee.append(mimeMessage, USER, STORAGE_DIRECTIVE).block()
    val messages = mailboxManager.getMailbox(TEAM_MAILBOX.mailboxPath("folder"), tmSession)
      .getMessages(MessageRange.all, FetchGroup.FULL_CONTENT, tmSession)

    assertThat(messages).toIterable.hasSize(1)
  }

  @Test
  def appendShouldAddMessageToDesiredTeamMailboxOmittingFolderValue(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block()
    testee.append(mimeMessage, USER, STORAGE_DIRECTIVE).block()
    val messages = mailboxManager.getMailbox(TEAM_MAILBOX.mailboxPath("folder"), tmSession)
      .getMessages(MessageRange.all, FetchGroup.FULL_CONTENT, tmSession)

    assertThat(messages).toIterable.hasSize(1)
  }

  @Test
  def appendShouldNotAddMessageToUserWhenForTeamMailbox(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block()
    val mailboxPath = MailboxPath.forUser(USER, FOLDER)
    mailboxManager.createMailbox(mailboxPath, userSession)

    testee.append(mimeMessage, USER, STORAGE_DIRECTIVE).block()
    val messages = mailboxManager.getMailbox(mailboxPath, userSession)
      .getMessages(MessageRange.all, FetchGroup.FULL_CONTENT, userSession)

    assertThat(messages).toIterable.hasSize(0)
  }

  @RepeatedTest(20)
  def appendShouldNotFailInConcurrentEnvironmentForTeamMailbox(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block()

    ConcurrentTestRunner.builder
      .operation((a: Int, b: Int) => testee.append(mimeMessage, USER, STORAGE_DIRECTIVE).block())
      .threadCount(100)
      .runSuccessfullyWithin(Duration.ofMinutes(1))
  }

  /**
   * Tests copied from Apache James #MailboxAppenderImplTest class
   */
  @Test
  def appendShouldAddMessageToDesiredMailbox(): Unit = {
    testee.append(mimeMessage, USER, STORAGE_DIRECTIVE).block()
    val messages = mailboxManager.getMailbox(MailboxPath.forUser(USER, FOLDER), userSession)
      .getMessages(MessageRange.all, FetchGroup.FULL_CONTENT, userSession)

    assertThat(messages).toIterable.hasSize(1)
  }

  @Test
  def appendShouldAddMessageToDesiredMailboxWhenMailboxExists(): Unit = {
    val mailboxPath = MailboxPath.forUser(USER, FOLDER)
    mailboxManager.createMailbox(mailboxPath, userSession)
    testee.append(mimeMessage, USER, STORAGE_DIRECTIVE).block()

    val messages = mailboxManager.getMailbox(mailboxPath, userSession)
      .getMessages(MessageRange.all, FetchGroup.FULL_CONTENT, userSession)

    assertThat(messages).toIterable.hasSize(1)
  }

  @Test
  def appendShouldNotAppendToEmptyFolder(): Unit = {
    assertThatThrownBy(() => testee.append(mimeMessage, USER, StorageDirective.builder()
      .targetFolder(EMPTY_FOLDER)
      .build()).block())
      .isInstanceOf(classOf[MessagingException])
  }

  @Test
  def appendShouldRemovePathSeparatorAsFirstChar(): Unit = {
    testee.append(mimeMessage, USER, StorageDirective.builder()
      .targetFolder(".folder")
      .build()).block()
    val messages = mailboxManager.getMailbox(MailboxPath.forUser(USER, FOLDER), userSession)
      .getMessages(MessageRange.all, FetchGroup.FULL_CONTENT, userSession)

    assertThat(messages).toIterable.hasSize(1)
  }

  @Test
  def appendShouldReplaceSlashBySeparator(): Unit = {
    testee.append(mimeMessage, USER, StorageDirective.builder()
      .targetFolder(FOLDER + "/any")
      .build()).block()

    val messages = mailboxManager.getMailbox(MailboxPath.forUser(USER, FOLDER + ".any"), userSession)
      .getMessages(MessageRange.all, FetchGroup.FULL_CONTENT, userSession)

    assertThat(messages).toIterable.hasSize(1)
  }

  @RepeatedTest(20)
  def appendShouldNotFailInConcurrentEnvironment(): Unit = {
    ConcurrentTestRunner.builder
      .operation((a: Int, b: Int) => testee.append(mimeMessage, USER, STORAGE_DIRECTIVE).block())
      .threadCount(100)
      .runSuccessfullyWithin(Duration.ofMinutes(1))
  }
}
