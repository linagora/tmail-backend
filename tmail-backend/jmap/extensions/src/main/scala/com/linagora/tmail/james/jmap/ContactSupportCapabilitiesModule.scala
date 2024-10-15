package com.linagora.tmail.james.jmap

import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CONTACT_SUPPORT
import jakarta.inject.Inject
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

case class ContactSupportProperties(mailAddress: MailAddress) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj("supportMailAddress" -> mailAddress.asString())
}

case class ContactSupportCapability(contactSupportProperties: ContactSupportProperties) extends Capability {
  val properties: CapabilityProperties = contactSupportProperties
  val identifier: CapabilityIdentifier = LINAGORA_CONTACT_SUPPORT
}

case class ContactSupportCapabilityFactory @Inject()(jmapConfig: JMAPExtensionConfiguration) extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability =
    ContactSupportCapability(ContactSupportProperties(new MailAddress(s"hnasri-${jmapConfig.publicAssetTotalSizeLimit.asLong()}@linagora.com")))

  override def id(): CapabilityIdentifier = LINAGORA_CONTACT_SUPPORT
}

class ContactSupportCapabilitiesModule() extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[ContactSupportCapabilityFactory])
  }
}
