package com.linagora.tmail.james.jmap

import java.io.FileNotFoundException

import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides, Singleton}
import org.apache.commons.configuration2.{Configuration, PropertiesConfiguration}
import org.apache.james.utils.PropertiesProvider

import scala.util.Try

class TMailJMAPModule extends AbstractModule {

  val JMAP_CONFIGURATION_EMPTY: Configuration = new PropertiesConfiguration()

  @Provides
  @Singleton
  @Named("jmap")
  def provideJMAPConfiguration(propertiesProvider: PropertiesProvider): Configuration =
    Try(propertiesProvider.getConfiguration("jmap"))
      .fold({
        case _: FileNotFoundException => JMAP_CONFIGURATION_EMPTY
      }, identity)

}
