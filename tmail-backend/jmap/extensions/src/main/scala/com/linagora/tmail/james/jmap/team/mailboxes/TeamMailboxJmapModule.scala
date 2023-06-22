package com.linagora.tmail.james.jmap.team.mailboxes

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.method.TeamMailboxRevokeAccessMethod
import com.linagora.tmail.team.TeamMailboxCallback
import org.apache.james.jmap.mail.NamespaceFactory
import org.apache.james.jmap.method.Method

class TeamMailboxJmapModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[NamespaceFactory]).to(classOf[TMailNamespaceFactory])
    install(new TeamMailboxesCapabilitiesModule())
    Multibinder.newSetBinder(binder, classOf[Method])
      .addBinding()
      .to(classOf[TeamMailboxRevokeAccessMethod])

    Multibinder.newSetBinder(binder, classOf[TeamMailboxCallback])
      .addBinding()
      .to(classOf[TeamMailboxAutocompleteCallback])
  }
}
