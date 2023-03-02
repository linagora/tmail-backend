package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.ProvidesIntoSet
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CALENDAR
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

case object CalendarCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = CalendarCapability

  override def id(): CapabilityIdentifier = LINAGORA_CALENDAR
}

case object CalendarCapability extends Capability {
  val properties: CapabilityProperties = CalendarCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_CALENDAR
}

case object CalendarCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

class CalendarCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = CalendarCapabilityFactory
}

class CalendarEventMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new CalendarCapabilitiesModule())
  }
}