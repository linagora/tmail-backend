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
 ********************************************************************/

package com.linagora.tmail.james.jmap.event

import java.io.StringReader

import com.linagora.tmail.james.jmap.label.MemoryLabelRepository
import com.linagora.tmail.james.jmap.model.Label
import org.apache.commons.configuration2.XMLConfiguration
import org.apache.commons.configuration2.io.FileHandler
import org.apache.james.core.Username
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.MailboxPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

class LabelProvisionnerTest {
  private val USER = Username.of("user@example.com")

  private var labelRepository: MemoryLabelRepository = _
  private var mailboxManager: org.apache.james.mailbox.inmemory.InMemoryMailboxManager = _

  @BeforeEach
  def setUp(): Unit = {
    val resources = InMemoryIntegrationResources.defaultResources()
    mailboxManager = resources.getMailboxManager
    labelRepository = new MemoryLabelRepository()
  }

  private def registerListener(rawXmlConfig: String): Unit = {
    val xmlConfiguration = new XMLConfiguration()
    val fileHandler = new FileHandler(xmlConfiguration)
    fileHandler.load(new StringReader(rawXmlConfig))
    val listenerConfig = xmlConfiguration.configurationAt("configuration")
    val testee = new LabelProvisionner(labelRepository, listenerConfig)
    mailboxManager.getEventBus.register(testee)
  }

  private def createInbox(): Unit =
    Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(USER), mailboxManager.createSystemSession(USER)))
      .subscribeOn(Schedulers.boundedElastic())
      .block()

  private def listLabels(): java.util.List[Label] =
    Flux.from(labelRepository.listLabels(USER))
      .collectList()
      .block()

  @Test
  def shouldCreateLabelsWhenInboxIsCreated(): Unit = {
    registerListener(
      """<listener>
        |  <configuration>
        |    <labels>
        |      <label>
        |        <keyword>social</keyword>
        |        <displayName>Social</displayName>
        |        <readOnly>true</readOnly>
        |        <color>#aabbcc</color>
        |        <description>Email from social networks</description>
        |      </label>
        |      <label>
        |        <displayName>Work</displayName>
        |        <readOnly>false</readOnly>
        |        <color>#112233</color>
        |      </label>
        |    </labels>
        |  </configuration>
        |</listener>""".stripMargin)

    createInbox()

    val labels = listLabels()
    assertThat(labels).hasSize(2)
  }

  @Test
  def labelWithKeywordShouldUseItAsStableId(): Unit = {
    registerListener(
      """<listener>
        |  <configuration>
        |    <labels>
        |      <label>
        |        <keyword>social</keyword>
        |        <displayName>Social</displayName>
        |        <readOnly>false</readOnly>
        |      </label>
        |    </labels>
        |  </configuration>
        |</listener>""".stripMargin)

    createInbox()

    val label = listLabels().asScala.head
    assertThat(label.keyword.flagName).isEqualTo("social")
  }

  @Test
  def readOnlyLabelShouldBeMarkedReadOnly(): Unit = {
    registerListener(
      """<listener>
        |  <configuration>
        |    <labels>
        |      <label>
        |        <keyword>promotion</keyword>
        |        <displayName>Promotion</displayName>
        |        <readOnly>true</readOnly>
        |        <color>#aabbcc</color>
        |      </label>
        |    </labels>
        |  </configuration>
        |</listener>""".stripMargin)

    createInbox()

    val label = listLabels().asScala.head
    assertThat(label.readOnly).isTrue
  }

  @Test
  def notReadOnlyLabelShouldNotBeMarkedReadOnly(): Unit = {
    registerListener(
      """<listener>
        |  <configuration>
        |    <labels>
        |      <label>
        |        <keyword>work</keyword>
        |        <displayName>Work</displayName>
        |        <readOnly>false</readOnly>
        |      </label>
        |    </labels>
        |  </configuration>
        |</listener>""".stripMargin)

    createInbox()

    val label = listLabels().asScala.head
    assertThat(label.readOnly).isFalse
  }

  @Test
  def labelColorAndDescriptionShouldBeStored(): Unit = {
    registerListener(
      """<listener>
        |  <configuration>
        |    <labels>
        |      <label>
        |        <keyword>social</keyword>
        |        <displayName>Social</displayName>
        |        <readOnly>false</readOnly>
        |        <color>#aabbcc</color>
        |        <description>Mail from social networks</description>
        |      </label>
        |    </labels>
        |  </configuration>
        |</listener>""".stripMargin)

    createInbox()

    val label = listLabels().asScala.head
    assertThat(label.color.map(_.value).getOrElse("")).isEqualTo("#aabbcc")
    assertThat(label.description.getOrElse("")).isEqualTo("Mail from social networks")
  }

  @Test
  def shouldNotCreateLabelsWhenOtherMailboxIsCreated(): Unit = {
    registerListener(
      """<listener>
        |  <configuration>
        |    <labels>
        |      <label>
        |        <keyword>social</keyword>
        |        <displayName>Social</displayName>
        |        <readOnly>false</readOnly>
        |      </label>
        |    </labels>
        |  </configuration>
        |</listener>""".stripMargin)

    Mono.from(mailboxManager.createMailboxReactive(MailboxPath.forUser(USER, "Trash"), mailboxManager.createSystemSession(USER)))
      .subscribeOn(Schedulers.boundedElastic())
      .block()

    assertThat(listLabels()).isEmpty()
  }

  @Test
  def shouldCreateNoLabelsWhenConfigurationIsEmpty(): Unit = {
    registerListener(
      """<listener>
        |  <configuration>
        |    <labels/>
        |  </configuration>
        |</listener>""".stripMargin)

    createInbox()

    assertThat(listLabels()).isEmpty()
  }

  @Test
  def labelWithoutKeywordShouldReceiveGeneratedKeyword(): Unit = {
    registerListener(
      """<listener>
        |  <configuration>
        |    <labels>
        |      <label>
        |        <displayName>Family</displayName>
        |        <readOnly>false</readOnly>
        |      </label>
        |    </labels>
        |  </configuration>
        |</listener>""".stripMargin)

    createInbox()

    val labels = listLabels()
    assertThat(labels).hasSize(1)
    assertThat(labels.get(0).displayName.value).isEqualTo("Family")
  }

  @Test
  def labelWithInvalidColorShouldBeCreatedWithoutColor(): Unit = {
    registerListener(
      """<listener>
        |  <configuration>
        |    <labels>
        |      <label>
        |        <keyword>work</keyword>
        |        <displayName>Work</displayName>
        |        <readOnly>false</readOnly>
        |        <color>#invalid</color>
        |      </label>
        |    </labels>
        |  </configuration>
        |</listener>""".stripMargin)

    createInbox()

    val label = listLabels().asScala.head
    assertThat(label.color.isDefined).isFalse
  }
}
