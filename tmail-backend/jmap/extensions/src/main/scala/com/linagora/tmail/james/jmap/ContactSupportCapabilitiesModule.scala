package com.linagora.tmail.james.jmap

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CONTACT_SUPPORT
import jakarta.inject.Inject
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

case class ContactSupportProperties(mailAddress: Option[MailAddress]) extends CapabilityProperties {

  override def jsonify(): JsObject = {
    val mailAddressOpt = mailAddress.map(_.asString())

    val json = if (mailAddressOpt.isEmpty) {
      """
        {
          "supportMailAddress": null
        }"""
    } else {
      s"""
        {
          "supportMailAddress": "${mailAddressOpt.get}"
        }"""
    }

    Json.parse(json).as[JsObject]
  }
}

case class ContactSupportCapability(contactSupportProperties: ContactSupportProperties) extends Capability {
  val properties: CapabilityProperties = contactSupportProperties
  val identifier: CapabilityIdentifier = LINAGORA_CONTACT_SUPPORT
}

case class ContactSupportCapabilityFactory @Inject()(jmapConfig: JMAPExtensionConfiguration) extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability =
    ContactSupportCapability(ContactSupportProperties(jmapConfig.supportMailAddress))

  override def id(): CapabilityIdentifier = LINAGORA_CONTACT_SUPPORT
}

class ContactSupportCapabilitiesModule() extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[ContactSupportCapabilityFactory])
  }
}
