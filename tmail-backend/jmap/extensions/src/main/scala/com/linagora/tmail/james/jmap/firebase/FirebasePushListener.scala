/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.firebase

import java.time.Clock

import com.google.firebase.messaging.{FirebaseMessagingException, MessagingErrorCode}
import com.linagora.tmail.james.jmap.firebase.FirebasePushListener.{GROUP, LOGGER}
import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionHelper.isNotOutdatedSubscription
import com.linagora.tmail.james.jmap.model.FirebaseSubscription
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.events.EventListener.ReactiveGroupEventListener
import org.apache.james.events.{Event, Group}
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.change.{EmailDeliveryTypeName, StateChangeEvent}
import org.apache.james.jmap.core.{AccountId, StateChange}
import org.apache.james.lifecycle.api.Startable
import org.apache.james.user.api.DelegationStore
import org.apache.james.util.{MDCBuilder, ReactorUtils}
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.javaapi.CollectionConverters

case class FirebasePushListenerGroup() extends Group {}

object FirebasePushListener {
  val GROUP: FirebasePushListenerGroup = FirebasePushListenerGroup()
  private val LOGGER = LoggerFactory.getLogger(classOf[FirebasePushListener])
}

class FirebasePushListener @Inject()(subscriptionRepository: FirebaseSubscriptionRepository,
                                     delegationStore: DelegationStore,
                                     jmapSettingsRepository: JmapSettingsRepository,
                                     pushClient: FirebasePushClient,
                                     clock: Clock) extends ReactiveGroupEventListener {
  override def getDefaultGroup: Group = GROUP

  override def isHandling(event: Event): Boolean = event.isInstanceOf[StateChangeEvent]

  override def reactiveEvent(event: Event): Publisher[Void] =
    event match {
      case event: StateChangeEvent =>
        firebasePushEnabled(event.username)
          .flatMap {
            case true => SMono(pushToAccountOwnerAndDelegatees(event))
            case _ => noPush
          }
      case _ => noPush
    }

  private def firebasePushEnabled(username: Username): SMono[Boolean] =
    SMono.fromPublisher(jmapSettingsRepository.get(username))
      .map(_.settings.get(FirebasePushEnableSettingParser.key()))
      .map(FirebasePushEnableSettingParser.parse)
      .defaultIfEmpty(FirebasePushEnableSettingParser.ENABLED)
      .map(_.enabled)

  private def pushToAccountOwnerAndDelegatees(event: StateChangeEvent): Mono[Void] =
    SFlux.fromPublisher(delegationStore.authorizedUsers(event.username))
      .filterWhen(firebasePushEnabled(_))
      .concatWith(SMono.just(event.username))
      .flatMap(subscriptionRepository.list, ReactorUtils.DEFAULT_CONCURRENCY)
      .filter(isNotOutdatedSubscription(_, clock))
      .flatMap(sendNotification(_, event), ReactorUtils.DEFAULT_CONCURRENCY)
      .`then`()
      .asJava()
      .contextWrite(ReactorUtils.context("fcm", MDCBuilder.create().addToContext("user", event.username.asString())))
      .`then`()

  private def noPush: SMono[Void] = SMono.empty

  private def sendNotification(subscription: FirebaseSubscription, stateChangeEvent: StateChangeEvent): Publisher[Unit] =
    stateChangeEvent
      .asStateChange
      .filter(subscription.types.toSet)
      .fold(SMono.empty[Unit])(stateChange => SMono(pushClient.push(asPushRequest(stateChange, subscription)))
        .onErrorResume {
          case e: FirebaseMessagingException => e.getMessagingErrorCode match {
            case MessagingErrorCode.INVALID_ARGUMENT | MessagingErrorCode.UNREGISTERED =>
              SMono.fromPublisher(subscriptionRepository.revoke(stateChangeEvent.username, subscription.id))
                .`then`(SMono.fromPublisher(ReactorUtils.logAsMono(() => LOGGER.warn("Subscription with invalid FCM token is removed for user {} and subscription {}", stateChangeEvent.username.asString(), subscription.id.serialize, e))))
            case _ =>
              SMono.error(new RuntimeException(
                s"Unexpected error during push message to Firebase Cloud Messaging for user ${stateChangeEvent.username.asString()} and subscription ${subscription.id.serialize} and code ${e.getMessagingErrorCode.name()}", e))
          }
          case e => SMono.error(new RuntimeException(
            s"Unexpected error during push message to Firebase Cloud Messaging for user ${stateChangeEvent.username.asString()} and subscription ${subscription.id.serialize}", e))
        }
        .`then`())

  private def asPushRequest(stateChange: StateChange, subscription: FirebaseSubscription): FirebasePushRequest =
    new FirebasePushRequest(CollectionConverters.asJava(stateChange.changes
      .flatMap(accountIdToTypeState => accountIdToTypeState._2.changes
        .map(typeNameToState => evaluateKey(accountIdToTypeState._1, typeNameToState._1) -> typeNameToState._2.serialize))),
      subscription.token,
      urgency(stateChange))

  private def evaluateKey(accountId: AccountId, typeName: TypeName): String =
    accountId.id.value + ":" + typeName.asString()

  private def urgency(filterStateChange: StateChange): FirebasePushUrgency =
    if (containsEmailDelivery(filterStateChange)) {
      FirebasePushUrgency.HIGH
    } else {
      FirebasePushUrgency.NORMAL
    }

  private def containsEmailDelivery(filterStateChange: StateChange): Boolean =
    filterStateChange.changes
      .values
      .flatMap(_.changes.keys)
      .exists(_.equals(EmailDeliveryTypeName))
}

class FirebasePushListenerRegister extends Startable
