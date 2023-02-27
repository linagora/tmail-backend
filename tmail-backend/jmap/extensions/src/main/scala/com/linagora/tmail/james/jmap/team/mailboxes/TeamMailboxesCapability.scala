package com.linagora.tmail.james.jmap.team.mailboxes

import com.google.inject.AbstractModule
import com.google.inject.multibindings.ProvidesIntoSet
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_TEAM_MAILBOXES
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

case object TeamMailboxesProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object TeamMailboxesCapability extends Capability {
  val properties: CapabilityProperties = TeamMailboxesProperties
  val identifier: CapabilityIdentifier = LINAGORA_TEAM_MAILBOXES
}

case object TeamMailboxesCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = TeamMailboxesCapability

  override def id(): CapabilityIdentifier = LINAGORA_TEAM_MAILBOXES
}

class TeamMailboxesCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = TeamMailboxesCapabilityFactory
}
