package com.linagora.tmail.james.jmap.json

import org.apache.james.jmap.core.{AccountId, Id, UuidState}
import org.apache.james.mailbox.inmemory.{InMemoryId, InMemoryMessageId}
import org.apache.james.mailbox.model.MessageId
import eu.timepit.refined.auto._

object Fixture {
  lazy val ACCOUNT_ID: AccountId = AccountId("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8")
  lazy val STATE: UuidState = UuidState.fromString("6e0dd59d-660e-4d9b-b22f-0354479f47b4")
  lazy val MESSAGE_ID_FACTORY: MessageId.Factory = new InMemoryMessageId.Factory
  lazy val MAILBOX_ID_FACTORY: InMemoryId.Factory = new InMemoryId.Factory
}
