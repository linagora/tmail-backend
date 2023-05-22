package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.ProvidesIntoSet
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_MESSAGE_VAULT
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

case object MessageVaultCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object MessageVaultCapability extends Capability {
  val properties: CapabilityProperties = MessageVaultCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_MESSAGE_VAULT
}

case object MessageVaultCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = MessageVaultCapability

  override def id(): CapabilityIdentifier = LINAGORA_MESSAGE_VAULT
}

class MessageVaultCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = MessageVaultCapabilityFactory
}
