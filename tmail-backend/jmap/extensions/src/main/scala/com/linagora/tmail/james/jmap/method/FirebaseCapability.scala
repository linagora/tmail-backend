package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.ProvidesIntoSet
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FIREBASE
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

case object FirebaseCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object FirebaseCapability extends Capability {
  val properties: CapabilityProperties = FirebaseCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_FIREBASE
}

case object FirebaseCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = FirebaseCapability

  override def id(): CapabilityIdentifier = LINAGORA_FIREBASE
}

class FirebaseCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = FirebaseCapabilityFactory
}
