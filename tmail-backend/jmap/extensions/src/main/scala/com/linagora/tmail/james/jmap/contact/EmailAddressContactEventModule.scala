package com.linagora.tmail.james.jmap.contact

import com.google.inject.AbstractModule
import com.google.inject.multibindings.ProvidesIntoSet
import com.google.inject.name.Named
import org.apache.james.events.EventListener.ReactiveGroupEventListener

class EmailAddressContactEventModule extends AbstractModule {

  @Named("TMAIL_GROUP_EVENT_LISTENER")
  @ProvidesIntoSet
  def tmailGroupEventListener(tmailContactListener: EmailAddressContactListener): ReactiveGroupEventListener = tmailContactListener

}
