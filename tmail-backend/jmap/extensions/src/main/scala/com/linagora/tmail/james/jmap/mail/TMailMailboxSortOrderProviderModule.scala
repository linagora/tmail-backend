package com.linagora.tmail.james.jmap.mail

import com.google.inject.AbstractModule
import org.apache.james.jmap.mail.SortOrderProvider

class TMailMailboxSortOrderProviderModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[SortOrderProvider]).toInstance(TMailMailbox.sortOrderProvider)
  }
}
