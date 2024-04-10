package com.linagora.tmail.james.jmap.team.mailboxes

import com.linagora.tmail.james.jmap.contact.{ContactFields, EmailAddressContactSearchEngine}
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxCallback}
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class TeamMailboxAutocompleteCallback @Inject()(contactSearchEngine: EmailAddressContactSearchEngine) extends TeamMailboxCallback {

  def teamMailboxAdded(teamMailbox: TeamMailbox): Publisher[Void] =
    SMono.fromPublisher(contactSearchEngine.index(teamMailbox.domain, ContactFields(address = teamMailbox.asMailAddress,
      firstname = teamMailbox.mailboxName.asString())))
      .`then`()

  def teamMailboxRemoved(teamMailbox: TeamMailbox): Publisher[Void] =
    SMono.fromPublisher(contactSearchEngine.delete(teamMailbox.domain, teamMailbox.asMailAddress))
}
