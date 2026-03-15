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

import com.linagora.tmail.james.jmap.label.LabelRepository
import com.linagora.tmail.james.jmap.model.{Color, DisplayName, KeywordUtil, Label, LabelId}
import jakarta.inject.Inject
import org.apache.commons.configuration2.HierarchicalConfiguration
import org.apache.commons.configuration2.tree.ImmutableNode
import org.apache.james.core.Username
import org.apache.james.events.{Event, EventListener, Group}
import org.apache.james.jmap.mail.Keyword
import org.apache.james.mailbox.events.MailboxEvents
import org.apache.james.mailbox.model.MailboxConstants
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.publisher.{Flux, Mono}

import scala.jdk.CollectionConverters._

object LabelProvisionner {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[LabelProvisionner])

  class LabelProvisionnerGroup extends Group {}

  private val GROUP: LabelProvisionnerGroup = new LabelProvisionnerGroup()

  private[event] case class LabelConfig(keyword: Option[String],
                                        displayName: String,
                                        readOnly: Boolean,
                                        color: Option[String],
                                        description: Option[String])

  private[event] def parseConfiguration(config: HierarchicalConfiguration[ImmutableNode]): List[LabelConfig] =
    config.configurationsAt("labels.label").asScala.toList.map { labelConfig =>
      LabelConfig(
        keyword = Option(labelConfig.getString("keyword", null)),
        displayName = labelConfig.getString("displayName"),
        readOnly = labelConfig.getBoolean("readOnly", false),
        color = Option(labelConfig.getString("color", null)),
        description = Option(labelConfig.getString("description", null)))
    }
}

class LabelProvisionner @Inject()(labelRepository: LabelRepository,
                                   listenerConfig: HierarchicalConfiguration[ImmutableNode])
  extends EventListener.ReactiveGroupEventListener {

  import LabelProvisionner._

  private val labelsToProvision: List[LabelConfig] = parseConfiguration(listenerConfig)

  override def getDefaultGroup: Group = GROUP

  override def isHandling(event: Event): Boolean = isInboxCreated(event)

  private def isInboxCreated(event: Event): Boolean = event match {
    case mailboxAdded: MailboxEvents.MailboxAdded =>
      mailboxAdded.getMailboxPath.getName.equalsIgnoreCase(MailboxConstants.INBOX) &&
        mailboxAdded.getMailboxPath.getUser.equals(event.getUsername)
    case _ => false
  }

  override def reactiveEvent(event: Event): Publisher[Void] =
    Flux.fromIterable(labelsToProvision.asJava)
      .concatMap(config => provisionLabel(event.getUsername, config)
        .onErrorResume(e => {
          LOGGER.error("Failed to provision label '{}' for user {}", config.displayName, event.getUsername.asString(), e)
          Mono.empty()
        }))
      .`then`()

  private def provisionLabel(username: Username, config: LabelConfig): Mono[Void] =
    Mono.from(labelRepository.addLabel(username, toLabel(config)))

  private def toLabel(config: LabelConfig): Label = {
    val keyword: Keyword = config.keyword
      .flatMap(kw => Keyword.of(kw).toOption)
      .getOrElse(KeywordUtil.generate())
    val color: Option[Color] = config.color.flatMap(c => Color.validate(c).toOption)
    Label(
      id = LabelId.fromKeyword(keyword),
      displayName = DisplayName(config.displayName),
      keyword = keyword,
      color = color,
      description = config.description,
      readOnly = config.readOnly)
  }
}
