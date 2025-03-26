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

import java.time.{Clock, ZonedDateTime}
import java.util
import java.util.Optional

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionHelper.{evaluateExpiresTime, isInThePast}
import com.linagora.tmail.james.jmap.model.{DeviceClientIdInvalidException, ExpireTimeInvalidException, FirebaseSubscription, FirebaseSubscriptionCreationRequest, FirebaseSubscriptionExpiredTime, FirebaseSubscriptionId, FirebaseSubscriptionNotFoundException, TokenInvalidException}
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraDataDefinition
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.user.api.DeleteUserDataTaskStep
import org.reactivestreams.Publisher
import reactor.core.publisher.SynchronousSink
import reactor.core.scala.publisher.SMono

import scala.jdk.javaapi.{CollectionConverters, OptionConverters}

case class CassandraFirebaseSubscriptionRepositoryModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CassandraFirebaseSubscriptionRepository]).in(Scopes.SINGLETON)
    bind(classOf[FirebaseSubscriptionRepository]).to(classOf[CassandraFirebaseSubscriptionRepository])

    Multibinder.newSetBinder(binder, classOf[CassandraDataDefinition])
      .addBinding().toInstance(CassandraFirebaseSubscriptionTable.MODULE)

    bind(classOf[CassandraFirebaseSubscriptionDAO]).in(Scopes.SINGLETON)

    Multibinder.newSetBinder(binder, classOf[DeleteUserDataTaskStep])
      .addBinding()
      .to(classOf[FirebaseSubscriptionUserDeletionTaskStep])
  }
}

class CassandraFirebaseSubscriptionRepository @Inject()(dao: CassandraFirebaseSubscriptionDAO, clock: Clock) extends FirebaseSubscriptionRepository {

  override def save(username: Username, request: FirebaseSubscriptionCreationRequest): Publisher[FirebaseSubscription] = {
    val firebaseSubscription = FirebaseSubscription.from(request, evaluateExpiresTime(OptionConverters.toJava(request.expires.map(_.value)), clock))

    isDuplicatedDeviceClientId(username, request.deviceClientId.value)
      .handle((isDuplicatedDeviceClientId: Boolean, sink: SynchronousSink[AnyRef]) => {
        if (isInThePast(request.expires, clock)) {
          sink.error(ExpireTimeInvalidException(request.expires.get.value, "expires must be greater than now"))
        }
        if (isDuplicatedDeviceClientId) {
          sink.error(DeviceClientIdInvalidException(request.deviceClientId, "deviceClientId must be unique"))
        }})
      .`then`(isDuplicatedToken(username, request.token.value))
      .handle((isDuplicatedToken: Boolean, sink: SynchronousSink[AnyRef]) => {
        if (isDuplicatedToken) {
          sink.error(TokenInvalidException("token must be unique"))
        }})
      .`then`(dao.insert(username, firebaseSubscription))
  }

  override def updateExpireTime(username: Username, id: FirebaseSubscriptionId, newExpire: ZonedDateTime): Publisher[FirebaseSubscriptionExpiredTime] =
    SMono.just(newExpire)
      .handle((inputTime: ZonedDateTime, sink: SynchronousSink[AnyRef]) => {
        if (newExpire.isBefore(ZonedDateTime.now(clock))) {
          sink.error(ExpireTimeInvalidException(inputTime, "expires must be greater than now"))
        }})
      .`then`(retrieveByFirebaseSubscriptionId(username, id)
        .switchIfEmpty(SMono.error(FirebaseSubscriptionNotFoundException(id))))
        .flatMap((subscription: FirebaseSubscription) => dao.insert(username, subscription.withExpires(evaluateExpiresTime(Optional.of(newExpire), clock))))
        .map(_.expires)

  override def updateTypes(username: Username, id: FirebaseSubscriptionId, types: util.Set[TypeName]): Publisher[Void] =
    retrieveByFirebaseSubscriptionId(username, id)
      .switchIfEmpty(SMono.error(FirebaseSubscriptionNotFoundException(id)))
      .map(_.withTypes(CollectionConverters.asScala(types).toSeq))
      .flatMap(dao.insert(username, _))
      .`then`()

  override def revoke(username: Username, id: FirebaseSubscriptionId): Publisher[Void] =
    SMono.fromPublisher(retrieveByFirebaseSubscriptionId(username, id))
      .flatMap(subscription => dao.deleteOne(username, subscription.deviceClientId.value))
      .`then`()

  override def revoke(username: Username): Publisher[Void] =
    SMono.fromPublisher(dao.deleteAllSubscriptions(username))
      .`then`()

  override def get(username: Username, ids: util.Set[FirebaseSubscriptionId]): Publisher[FirebaseSubscription] =
    dao.selectAll(username)
      .filter(subscription => ids.contains(subscription.id))

  override def list(username: Username): Publisher[FirebaseSubscription] =
    dao.selectAll(username)

  private def isDuplicatedDeviceClientId(username: Username, deviceClientId: String): SMono[Boolean] =
    dao.selectAll(username)
      .filter(_.deviceClientId.value.equals(deviceClientId))
      .hasElements

  private def isDuplicatedToken(username: Username, deviceToken: String): SMono[Boolean] =
    dao.selectAll(username)
      .filter(_.token.value.equals(deviceToken))
      .hasElements

  private def retrieveByFirebaseSubscriptionId(username: Username, id: FirebaseSubscriptionId): SMono[FirebaseSubscription] =
    dao.selectAll(username).filter(_.id.equals(id)).next
}
