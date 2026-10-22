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

import com.linagora.tmail.jmap.aibot.agent.{AgentTool, AgentToolName}
import jakarta.inject.Inject
import jakarta.mail.Flags
import org.apache.james.mailbox.model.SearchQuery.Sort.{Order, SortClause}
import org.apache.james.mailbox.model.SearchQuery.{AddressType, Criterion, DateResolution, Sort}
import org.apache.james.mailbox.model.{MailboxId, MultimailboxesSearchQuery, SearchOptions, SearchQuery}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.util.streams.Limit
import play.api.libs.json.{JsObject, JsValue, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object SearchEmailsTool {
  val DEFAULT_LIMIT = 10
  val MAX_LIMIT = 20
}

class SearchEmailsTool @Inject()(val mailboxManager: MailboxManager,
                                 val mailboxIdFactory: MailboxId.Factory,
                                 val emailReader: ToolEmailReader) extends AgentTool {

  override val name: AgentToolName = AgentToolName("search_emails")

  override val definition: JsObject = Json.obj(
    "name" -> name.nameAsString,
    "description" -> ("Search the user's emails with filters. All filters are optional and combined with AND. " +
      "Returns matching emails, most recent first, with id, sender, subject, date and a short preview. " +
      "This is more expensive than list_emails: prefer list_emails first and use this tool only when filtering is needed. " +
      "Use the returned id with create_draft to reply to an email."),
    "parameters" -> Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(
        "from" -> Json.obj(
          "type" -> "string",
          "description" -> "Sender email address or name"),
        "to" -> Json.obj(
          "type" -> "string",
          "description" -> "Recipient email address or name"),
        "text" -> Json.obj(
          "type" -> "string",
          "description" -> "Text to search in the subject, addresses and body"),
        "after" -> Json.obj(
          "type" -> "string",
          "description" -> "Only emails received after this date (ISO 8601, e.g. 2026-10-14)"),
        "before" -> Json.obj(
          "type" -> "string",
          "description" -> "Only emails received before this date (ISO 8601, e.g. 2026-10-14)"),
        "inMailboxes" -> Json.obj(
          "type" -> "array",
          "items" -> Json.obj("type" -> "string"),
          "description" -> "Only emails in one of these mailbox ids. Searches all mailboxes if omitted"),
        "unread" -> Json.obj(
          "type" -> "boolean",
          "description" -> "Only return unread emails"),
        "limit" -> Json.obj(
          "type" -> "integer",
          "description" -> "Maximum number of results, default 10, max 20")),
      "required" -> Json.arr()))

  override def execute(session: MailboxSession, arguments: JsObject): SMono[JsValue] =
    SMono.fromCallable(() => {
      val args = ToolArguments(arguments)
      (query(args), args.limit("limit", SearchEmailsTool.DEFAULT_LIMIT, SearchEmailsTool.MAX_LIMIT))
    }).flatMap { case (query, limit) =>
      SFlux(mailboxManager.search(query, session, SearchOptions.limit(Limit.from(limit))))
        .collectSeq()
        .flatMap(ids => emailReader.summaries(session, ids))
        .map(emails => Json.obj("emails" -> emails.map(_.json)))
    }

  private def query(args: ToolArguments): MultimailboxesSearchQuery = {
    val text: Option[Criterion] = args.string("text").map(text => SearchQuery.or(Seq(
      SearchQuery.address(AddressType.From, text),
      SearchQuery.address(AddressType.To, text),
      SearchQuery.address(AddressType.Cc, text),
      SearchQuery.address(AddressType.Bcc, text),
      SearchQuery.subject(text),
      SearchQuery.bodyContains(text),
      SearchQuery.attachmentContains(text),
      SearchQuery.attachmentFileName(text)).asJava))
    val criteria: Seq[Criterion] = Seq(
      args.string("from").map(SearchQuery.address(AddressType.From, _)),
      args.string("to").map(SearchQuery.address(AddressType.To, _)),
      text,
      args.date("after").map(SearchQuery.internalDateAfter(_, DateResolution.Second)),
      args.date("before").map(SearchQuery.internalDateBefore(_, DateResolution.Second)),
      args.boolean("unread").map {
        case true => SearchQuery.flagIsUnSet(Flags.Flag.SEEN)
        case false => SearchQuery.flagIsSet(Flags.Flag.SEEN)
      }).flatten

    val searchQuery = SearchQuery.builder()
      .andCriteria((if (criteria.isEmpty) Seq(SearchQuery.all()) else criteria).asJava)
      .sorts(new Sort(SortClause.Arrival, Order.REVERSE))
      .build()
    val mailboxIds = args.strings("inMailboxes").map(ToolIds.mailboxId(mailboxIdFactory, _))
    val builder = MultimailboxesSearchQuery.from(searchQuery)
    if (mailboxIds.nonEmpty) builder.inMailboxes(mailboxIds.asJava).build() else builder.build()
  }
}
