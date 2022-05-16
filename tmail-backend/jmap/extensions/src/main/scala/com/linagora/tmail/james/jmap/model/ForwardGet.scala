package com.linagora.tmail.james.jmap.model

import eu.timepit.refined.auto._
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Id, Properties, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class UnparsedForwardId(id: Id)

case class ForwardIds(value: List[UnparsedForwardId])

case class ForwardGetRequest(accountId: AccountId,
                             ids: Option[ForwardIds],
                             properties: Option[Properties]) extends WithAccountId

case class ForwardNotFound(value: Set[UnparsedForwardId]) {
  def merge(other: ForwardNotFound): ForwardNotFound = ForwardNotFound(this.value ++ other.value)
}

case class ForwardId()
case class LocalCopy(value: Boolean) extends AnyVal
case class Forward(value: MailAddress)

object Forwards {
  val FORWARD_ID: Id = Id.validate("singleton").toOption.get
  val UNPARSED_SINGLETON: UnparsedForwardId = UnparsedForwardId(FORWARD_ID)

  def asRfc8621(forwards: List[MailAddress], mailAddress: MailAddress): Forwards = Forwards(
    id = ForwardId(),
    localCopy = LocalCopy(forwards.isEmpty || forwards.contains(mailAddress)),
    forwards = forwards.filter(!_.equals(mailAddress))
      .map(Forward)
  )

  val allProperties: Properties = Properties("id", "localCopy", "forwards")
  val idProperty: Properties = Properties("id")

  def propertiesFiltered(requestedProperties: Properties) : Properties = idProperty ++ requestedProperties
}

case class Forwards(id: ForwardId,
                    localCopy: LocalCopy,
                    forwards: List[Forward])

case class ForwardGetResponse(accountId: AccountId,
                              state: UuidState,
                              list: List[Forwards],
                              notFound: ForwardNotFound)


