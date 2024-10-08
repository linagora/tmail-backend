package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.ProvidesIntoSet
import com.linagora.tmail.james.jmap.firebase.FirebaseConfiguration
import com.linagora.tmail.james.jmap.json.FirebaseSubscriptionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FIREBASE
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.JsObject

import scala.jdk.javaapi.OptionConverters

case class FirebaseCapabilityProperties(apiKey: ApiKey,
                                        appId: AppId,
                                        messagingSenderId: MessagingSenderId,
                                        projectId: ProjectId,
                                        databaseUrl: DatabaseUrl,
                                        storageBucket: StorageBucket,
                                        authDomain: AuthDomain,
                                        vapidPublicKey: VapidPublicKey) extends CapabilityProperties {
  override def jsonify(): JsObject = FirebaseSubscriptionSerializer.firebaseCapabilityWrites.writes(this)
}

case class FirebaseCapability(properties: FirebaseCapabilityProperties) extends Capability {
  val identifier: CapabilityIdentifier = LINAGORA_FIREBASE
}

case class FirebaseCapabilityFactory(configuration: FirebaseConfiguration) extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = FirebaseCapability(FirebaseCapabilityProperties(
    ApiKey(OptionConverters.toScala(configuration.apiKey())),
    AppId(OptionConverters.toScala(configuration.appId())),
    MessagingSenderId(OptionConverters.toScala(configuration.messagingSenderId())),
    ProjectId(OptionConverters.toScala(configuration.projectId())),
    DatabaseUrl(OptionConverters.toScala(configuration.databaseUrl())),
    StorageBucket(OptionConverters.toScala(configuration.storageBucket())),
    AuthDomain(OptionConverters.toScala(configuration.authDomain())),
    VapidPublicKey(OptionConverters.toScala(configuration.vapidPublicKey()))))

  override def id(): CapabilityIdentifier = LINAGORA_FIREBASE
}

case class ApiKey(value: Option[String])
case class AppId(value: Option[String])
case class MessagingSenderId(value: Option[String])
case class ProjectId(value: Option[String])
case class DatabaseUrl(value: Option[String])
case class StorageBucket(value: Option[String])
case class AuthDomain(value: Option[String])
case class VapidPublicKey(value: Option[String])

class FirebaseCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(configuration: FirebaseConfiguration): CapabilityFactory = FirebaseCapabilityFactory(configuration)
}
