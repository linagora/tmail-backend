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

package com.linagora.tmail.james.jmap.method

import java.time.Instant
import java.util.Optional

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration
import com.linagora.tmail.james.jmap.method.KeywordEmailQueryViewOptimizer.FORBIDDEN_SYSTEM_FLAGS
import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView
import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView.Options
import jakarta.inject.Inject
import org.apache.james.jmap.core.Limit.Limit
import org.apache.james.jmap.core.Position.Position
import org.apache.james.jmap.mail.{CollapseThreads, Comparator, EmailQueryRequest, FilterCondition, FilterQuery, Keyword}
import org.apache.james.jmap.method.EmailQueryOptimizer
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MultimailboxesSearchQuery.Namespace
import org.apache.james.mailbox.model.{MessageId, MultimailboxesSearchQuery}
import org.apache.james.util.streams.{Limit => JavaLimit}
import reactor.core.scala.publisher.SFlux

import scala.jdk.OptionConverters._

object KeywordEmailQueryViewOptimizer {
  val FORBIDDEN_SYSTEM_FLAGS = List(Keyword.RECENT, Keyword.DELETED, Keyword.DRAFT, Keyword.ANSWERED, Keyword.FORWARDED, Keyword.SEEN)
}

class KeywordEmailQueryViewOptimizer @Inject() (val configuration: JMAPExtensionConfiguration,
                                                val keywordEmailQueryView: KeywordEmailQueryView) extends EmailQueryOptimizer {
  override def apply(request: EmailQueryRequest, session: MailboxSession, searchQuery: MultimailboxesSearchQuery, position: Position, limit: Limit): Option[SFlux[MessageId]] =
    request match {
      case request: EmailQueryRequest if matchesInKeywordSortedByReceivedAt(request) =>
        Some(keywordQueryViewForListingSortedByReceivedAt(session, position, limit, request, searchQuery.getNamespace))
      case _ => None
    }

  private def matchesInKeywordSortedByReceivedAt(request: EmailQueryRequest): Boolean =
    configuration.viewKeywordQueryEnabled &&
      request.filter.nonEmpty &&
      isValidKeywordQuery(request) &&
      request.sort.contains(Set(Comparator.RECEIVED_AT_DESC))

  private def isValidKeywordQuery(request: EmailQueryRequest): Boolean = {
    val filterQuery: FilterQuery = request.filter.get

    keywordOnlyFilter(filterQuery) &&
      isAllowedKeyword(filterQuery)
  }

  private def keywordOnlyFilter(filterQuery: FilterQuery): Boolean =
    filterQuery match {
      case condition: FilterCondition =>
        condition.hasKeyword.nonEmpty &&
          condition.inMailbox.isEmpty &&
          condition.inMailboxOtherThan.isEmpty &&
          condition.notKeyword.isEmpty &&
          condition.minSize.isEmpty &&
          condition.maxSize.isEmpty &&
          condition.hasAttachment.isEmpty &&
          condition.allInThreadHaveKeyword.isEmpty &&
          condition.someInThreadHaveKeyword.isEmpty &&
          condition.noneInThreadHaveKeyword.isEmpty &&
          condition.text.isEmpty &&
          condition.from.isEmpty &&
          condition.to.isEmpty &&
          condition.cc.isEmpty &&
          condition.bcc.isEmpty &&
          condition.subject.isEmpty &&
          condition.header.isEmpty &&
          condition.body.isEmpty
      case _ => false
    }

  private def isAllowedKeyword(filterQuery: FilterQuery): Boolean = {
    val keyword: Keyword = filterQuery.asInstanceOf[FilterCondition]
      .hasKeyword
      .get

    !FORBIDDEN_SYSTEM_FLAGS.contains(keyword)
  }

  private def keywordQueryViewForListingSortedByReceivedAt(mailboxSession: MailboxSession, position: Position, limitToUse: Limit, request: EmailQueryRequest, namespace: Namespace): SFlux[MessageId] = {
    val condition: FilterCondition = request.filter.get.asInstanceOf[FilterCondition]
    val keyword: Keyword = condition.hasKeyword.get
    val collapseThreads: Boolean = request.collapseThreads.getOrElse(CollapseThreads(false)).value
    val before: Optional[Instant] = condition.before.map(_.asUTC.toInstant).toJava
    val after: Optional[Instant] = condition.after.map(_.asUTC.toInstant).toJava

    val options: Options = new Options(before, after, JavaLimit.from(limitToUse.value + position.value), collapseThreads)

    SFlux.fromPublisher(keywordEmailQueryView
        .listMessagesByKeyword(mailboxSession.getUser, keyword, options))
      .drop(position.value)
      .take(limitToUse.value)
  }
}
