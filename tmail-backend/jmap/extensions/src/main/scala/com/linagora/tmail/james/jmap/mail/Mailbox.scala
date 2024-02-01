package com.linagora.tmail.james.jmap.mail

import eu.timepit.refined.auto._
import org.apache.james.jmap.mail.SortOrder.defaultSortOrder
import org.apache.james.jmap.mail.{SortOrder, SortOrderProvider}
import org.apache.james.mailbox.Role

object TMailMailbox {
  private val sortOrdersMap: Map[Role, SortOrder] = Map(
    Role.INBOX -> SortOrder(10L),
    Role.SENT -> SortOrder(20L),
    Role.ARCHIVE -> SortOrder(30L),
    Role.DRAFTS -> SortOrder(40L),
    Role.OUTBOX -> SortOrder(50L),
    Role.TRASH -> SortOrder(60L),
    Role.SPAM -> SortOrder(70L),
    Role.TEMPLATES -> SortOrder(80L),
    Role.RESTORED_MESSAGES -> SortOrder(90L))
    .withDefaultValue(defaultSortOrder)

  val sortOrderProvider: SortOrderProvider = role => sortOrdersMap(role)
}
