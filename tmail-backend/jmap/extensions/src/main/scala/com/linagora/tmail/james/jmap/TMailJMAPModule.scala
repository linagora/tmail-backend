package com.linagora.tmail.james.jmap

import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides, Singleton}
import org.apache.commons.configuration2.Configuration
import org.apache.james.utils.PropertiesProvider

class TMailJMAPModule extends AbstractModule {

  @Provides
  @Singleton
  @Named("jmap")
  def provideJMAPConfiguration(propertiesProvider: PropertiesProvider): Configuration =
    propertiesProvider.getConfiguration("jmap")

}
