package com.linagora.tmail.james.jmap.team.mailboxes

import com.google.inject.AbstractModule
import org.apache.james.jmap.mail.NamespaceFactory

class TeamMailboxJmapModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[NamespaceFactory]).to(classOf[TMailNamespaceFactory])
    install(new TeamMailboxesCapabilitiesModule())
  }
}
