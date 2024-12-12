package com.linagora.tmail.james.app.modules.jmap

import com.google.inject.multibindings.ProvidesIntoSet
import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys
import com.linagora.tmail.james.jmap.contact.{EmailAddressContactListener, InMemoryEmailAddressContactSearchEngineModule}
import org.apache.james.events.EventBus
import org.apache.james.lifecycle.api.Startable
import org.apache.james.utils.{InitializationOperation, InitilizationOperationBuilder}

class MemoryEmailAddressContactModule extends AbstractModule {

  override def configure(): Unit = {
    install(new InMemoryEmailAddressContactSearchEngineModule)
  }

  @ProvidesIntoSet
  def registerListener(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) eventBus: EventBus,
                       emailAddressContactListener: EmailAddressContactListener): InitializationOperation = {
    InitilizationOperationBuilder.forClass(classOf[EmailAddressContactEventLoader])
      .init(() => eventBus.register(emailAddressContactListener))
  }

  @Provides
  @Singleton
  @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
  def provideInVMEventBus(eventBus: EventBus): EventBus = eventBus

}

class EmailAddressContactEventLoader extends Startable
