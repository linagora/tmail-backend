package com.linagora.tmail.james.jmap.model

import java.util.UUID

import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Limit.Limit
import org.apache.james.jmap.core.{AccountId, LimitUnparsed}
import org.apache.james.jmap.method.WithAccountId

case class ContactAutocompleteRequest(accountId: AccountId,
                                      filter: ContactFilter,
                                      limit: Option[LimitUnparsed]) extends WithAccountId

case class ContactText(value: String)

object ContactFilter{
  val SUPPORTED: Set[String] = Set("text")
}
case class ContactFilter(text: ContactText)

case class ContactAutocompleteResponse(accountId: AccountId,
                                       list: Seq[Contact],
                                       limit: Option[Limit])

case class ContactId(value: UUID)
case class ContactFirstname(value: String)
case class ContactSurname(value: String)
case class Contact(id: ContactId,
                   emailAddress: MailAddress,
                   firstname: ContactFirstname,
                   surname: ContactSurname)