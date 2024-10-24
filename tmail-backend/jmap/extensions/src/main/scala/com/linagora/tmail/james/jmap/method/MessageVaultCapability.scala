package com.linagora.tmail.james.jmap.method

import java.time.Duration

import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_MESSAGE_VAULT
import org.apache.commons.lang3.time.DurationFormatUtils
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

case class MessageVaultCapabilityProperties(maxEmailRecoveryPerRequest: Long, restorationHorizon: Duration) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj(
    "maxEmailRecoveryPerRequest" -> maxEmailRecoveryPerRequest.toString,
    "restorationHorizon" -> DurationFormatUtils.formatDurationWords(restorationHorizon.toMillis, true, true))
}

case class MessageVaultCapability(messageVaultCapabilityProperties: MessageVaultCapabilityProperties) extends Capability {
  val identifier: CapabilityIdentifier = LINAGORA_MESSAGE_VAULT
  val properties: MessageVaultCapabilityProperties = messageVaultCapabilityProperties
}

case class MessageVaultCapabilityFactory @Inject()(configuration: EmailRecoveryActionConfiguration) extends CapabilityFactory {
  override def id(): CapabilityIdentifier = LINAGORA_MESSAGE_VAULT

  override def create(urlPrefixes: UrlPrefixes): Capability = {
    MessageVaultCapability(MessageVaultCapabilityProperties(
      maxEmailRecoveryPerRequest = configuration.maxEmailRecoveryPerRequest,
      restorationHorizon = configuration.restorationHorizon))
  }
}

class MessageVaultCapabilitiesModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[MessageVaultCapabilityFactory])
  }
}
