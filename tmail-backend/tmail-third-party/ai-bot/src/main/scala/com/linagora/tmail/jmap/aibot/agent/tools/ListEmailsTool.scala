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
package com.linagora.tmail.jmap.aibot.agent.tools

import com.linagora.tmail.jmap.aibot.agent.{AgentTool, AgentToolName, InvalidToolCallException}
import jakarta.inject.Inject
import org.apache.james.jmap.JMAPConfiguration
import org.apache.james.jmap.api.projections.EmailQueryViewManager
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.SearchQuery.Sort
import org.apache.james.mailbox.model.SearchQuery.Sort.{Order, SortClause}
import org.apache.james.mailbox.model.{MailboxId, MailboxPath, MessageId, MultimailboxesSearchQuery, SearchOptions, SearchQuery}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.util.streams.{Limit, Offset}
import play.api.libs.json.{JsObject, JsValue, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

case class ListEmailsRequest(mailboxId: Option[MailboxId], page: Int, limit: Int) {
  def offset: Int = (page - 1) * limit
}

object ListEmailsTool {
  val MAILBOX_ID: ArgumentName = ArgumentName("mailboxId")
  val PAGE: ArgumentName = ArgumentName("page")
  val LIMIT: ArgumentName = ArgumentName("limit")
  val PAGE_SIZE: PageSize = PageSize(default = 10, max = 20)
  val MAX_PAGE = 50
  val COLLAPSE_THREADS = false
}

class ListEmailsTool @Inject()(val mailboxManager: MailboxManager,
                               val mailboxIdFactory: MailboxId.Factory,
                               val jmapConfiguration: JMAPConfiguration,
                               val emailQueryViewManager: EmailQueryViewManager,
                               val emailReader: ToolEmailReader) extends AgentTool {

  override val name: AgentToolName = AgentToolName("list_emails")

  override val definition: JsObject = Json.obj(
    "name" -> name.nameAsString,
    "description" -> ("List the user's emails in a mailbox as seen by the user, most recent first. " +
      "Returns id, sender, subject, date and a short preview for each email. " +
      "This is cheaper than search_emails: favour this tool and try it before searching. " +
      "Only fall back to search_emails when listing did not help to find the emails."),
    "parameters" -> Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(
        ListEmailsTool.MAILBOX_ID.value -> Json.obj(
          "type" -> "string",
          "description" -> "Id of the mailbox to list emails from. Defaults to the Inbox."),
        ListEmailsTool.PAGE.value -> Json.obj(
          "type" -> "integer",
          "description" -> "Page number to retrieve, starting at 1, default 1, max 50"),
        ListEmailsTool.LIMIT.value -> Json.obj(
          "type" -> "integer",
          "description" -> "Maximum number of results per page, default 10, max 20")),
      "required" -> Json.arr()))

  override def execute(session: MailboxSession, arguments: JsObject): SMono[JsValue] =
    SMono.fromCallable(() => request(ToolArguments(arguments)))
      .flatMap(request => mailbox(session, request.mailboxId)
        .flatMap(mailboxId => listIds(session, mailboxId, request)
          .flatMap(ids => emailReader.summaries(session, ids))
          .map(emails => response(Some(mailboxId), request, emails)))
        .switchIfEmpty(SMono.fromCallable(() => response(None, request, Seq()))))

  private def response(mailboxId: Option[MailboxId], request: ListEmailsRequest, emails: Seq[ToolEmailSummary]): JsValue =
    Json.obj(
      "mailboxId" -> mailboxId.map(_.serialize()),
      "page" -> request.page,
      "limit" -> request.limit,
      "emails" -> emails.map(_.json))

  private def request(args: ToolArguments): ListEmailsRequest = {
    val page: Int = args.int(ListEmailsTool.PAGE).getOrElse(1)
    if (page < 1) {
      throw InvalidToolCallException(s"'${ListEmailsTool.PAGE.value}' must be positive")
    }
    if (page > ListEmailsTool.MAX_PAGE) {
      throw InvalidToolCallException(s"'${ListEmailsTool.PAGE.value}' must be at most ${ListEmailsTool.MAX_PAGE}")
    }
    ListEmailsRequest(
      mailboxId = args.string(ListEmailsTool.MAILBOX_ID).map(id => ToolIds.mailboxId(mailboxIdFactory, id)),
      page = page,
      limit = args.limit(ListEmailsTool.LIMIT, ListEmailsTool.PAGE_SIZE))
  }

  private def listIds(session: MailboxSession, mailboxId: MailboxId, request: ListEmailsRequest): SMono[Seq[MessageId]] =
    if (jmapConfiguration.isEmailQueryViewEnabled) {
      SFlux(emailQueryViewManager.getEmailQueryView(session.getUser)
          .listMailboxContentSortedByReceivedAt(mailboxId, Limit.from(request.offset + request.limit), ListEmailsTool.COLLAPSE_THREADS))
        .drop(request.offset)
        .collectSeq()
    } else {
      SFlux(mailboxManager.search(query(mailboxId), session,
          SearchOptions.of(Offset.from(request.offset), Limit.from(request.limit))))
        .collectSeq()
    }

  private def query(mailboxId: MailboxId): MultimailboxesSearchQuery =
    MultimailboxesSearchQuery.from(SearchQuery.allSortedWith(new Sort(SortClause.Arrival, Order.REVERSE)))
      .inMailboxes(java.util.List.of(mailboxId))
      .build()

  private def mailbox(session: MailboxSession, mailboxId: Option[MailboxId]): SMono[MailboxId] =
    mailboxId match {
      case Some(id) => SMono(mailboxManager.getMailboxReactive(id, session)).map(_.getId)
      case None => SMono(mailboxManager.getMailboxReactive(MailboxPath.inbox(session), session))
        .map(_.getId)
        .onErrorResume {
          case _: MailboxNotFoundException => SMono.empty
          case e => SMono.error(e)
        }
    }
}
