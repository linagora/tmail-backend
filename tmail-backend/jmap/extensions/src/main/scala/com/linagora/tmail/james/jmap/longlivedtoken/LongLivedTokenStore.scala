package com.linagora.tmail.james.jmap.longlivedtoken

import com.google.common.base.Preconditions
import com.google.inject.{AbstractModule, Scopes}
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import org.apache.james.core.Username
import org.apache.james.jmap.core.Id.Id
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import java.util.UUID
import scala.collection.concurrent.Map
import scala.util.Try

object LongLivedTokenId {
  def parse(string: String): Either[IllegalArgumentException, LongLivedTokenId] = Try(UUID.fromString(string))
    .map(LongLivedTokenId(_))
    .fold(e => Left(new IllegalArgumentException("LongLivedTokenId must be backed by a UUID", e)), Right(_))

  def generate: LongLivedTokenId = LongLivedTokenId(UUID.randomUUID())
}

object LongLivedTokenSecret {
  def parse(string: String): Either[IllegalArgumentException, LongLivedTokenSecret] = Try(UUID.fromString(string))
    .map(LongLivedTokenSecret(_))
    .fold(e => Left(new IllegalArgumentException("LongLivedTokenSecret must be backed by a UUID", e)), Right(_))

  def generate: LongLivedTokenSecret = LongLivedTokenSecret(UUID.randomUUID())
}

object DeviceId {
  val MAX_LENGTH: Int = 500
}

case class DeviceId(value: String) extends AnyVal {

  def validate: Either[IllegalArgumentException, DeviceId] = refineV[NonEmpty](value) match {
    case Left(e) => Left(new IllegalArgumentException("deviceId must not be empty"))
    case Right(_) => validateMaxLength
  }

  def validateMaxLength: Either[IllegalArgumentException, DeviceId] =
    if (value.length > DeviceId.MAX_LENGTH) {
      Left(new IllegalArgumentException("Length of deviceId must be smaller than " + DeviceId.MAX_LENGTH))
    } else {
      scala.Right(this)
    }
}

case class UnparsedLongLivedTokenId(value: Id)

case class LongLivedTokenId(value: UUID)

case class LongLivedTokenSecret(value: UUID)

case class LongLivedToken(deviceId: DeviceId, secret: LongLivedTokenSecret)

case class LongLivedTokenFootPrint(id: LongLivedTokenId, deviceId: DeviceId)

case class LongLivedTokenNotFoundException() extends RuntimeException

case class LongLivedTokenInfo(id: LongLivedTokenId, deviceId: DeviceId, secret: LongLivedTokenSecret) {
  def asFootPrint(): LongLivedTokenFootPrint = LongLivedTokenFootPrint(id, deviceId)
}

trait LongLivedTokenStore {
  def store(username: Username, longLivedToken: LongLivedToken): Publisher[LongLivedTokenId]

  def validate(username: Username, secret: LongLivedTokenSecret): Publisher[LongLivedTokenFootPrint]

  def listTokens(user: Username): Publisher[LongLivedTokenFootPrint]

  def revoke(username: Username, id: LongLivedTokenId): Publisher[Unit]
}

case class LongLivedTokenStoreInMemoryModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[InMemoryLongLivedTokenStore]).in(Scopes.SINGLETON)
    bind(classOf[LongLivedTokenStore]).to(classOf[InMemoryLongLivedTokenStore])
  }
}

class InMemoryLongLivedTokenStore() extends LongLivedTokenStore {

  val tokenStore: Map[Username, Seq[LongLivedTokenInfo]] = scala.collection.concurrent.TrieMap()

  override def store(username: Username, longLivedToken: LongLivedToken): Publisher[LongLivedTokenId] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(longLivedToken)
    SMono.just(tokenStore.contains(username))
      .map(userExists =>
        if (userExists) {
          storeWhenUserExists(username, longLivedToken)
        } else {
          storeWhenUserNotExists(username, longLivedToken)
        })
  }

  private def storeWhenUserExists(username: Username, longLivedToken: LongLivedToken): LongLivedTokenId = {
    val tokenList: Seq[LongLivedTokenInfo] = tokenStore(username)
    tokenList
      .find(tokenInfo => tokenInfo.deviceId.equals(longLivedToken.deviceId) && tokenInfo.secret.equals(longLivedToken.secret))
      .map(e => e.id)
      .getOrElse({
        val longLivedTokenId: LongLivedTokenId = LongLivedTokenId.generate
        tokenStore.put(username, tokenList :+ LongLivedTokenInfo(longLivedTokenId, longLivedToken.deviceId, longLivedToken.secret))
        longLivedTokenId
      })
  }

  private def storeWhenUserNotExists(username: Username, longLivedToken: LongLivedToken): LongLivedTokenId = {
    val longLivedTokenId: LongLivedTokenId = LongLivedTokenId.generate
    tokenStore.put(username, Seq(LongLivedTokenInfo(longLivedTokenId, longLivedToken.deviceId, longLivedToken.secret)))
    longLivedTokenId
  }

  override def validate(username: Username, secret: LongLivedTokenSecret): Publisher[LongLivedTokenFootPrint] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(secret)
    SFlux.fromIterable(tokenStore.getOrElse(username, Seq.empty))
      .filter(row => row.secret.equals(secret))
      .head
      .map(tokenInfo => tokenInfo.asFootPrint())
      .switchIfEmpty(SMono.error(new LongLivedTokenNotFoundException))
  }

  override def listTokens(username: Username): Publisher[LongLivedTokenFootPrint] = {
    Preconditions.checkNotNull(username)
    SFlux.fromIterable(tokenStore.getOrElse(username, Seq.empty))
      .map(tokenInfo => tokenInfo.asFootPrint())
  }

  override def revoke(username: Username, id: LongLivedTokenId): Publisher[Unit] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(id)
    val tokenList: Seq[LongLivedTokenInfo] = tokenStore.getOrElse(username, Seq.empty)

    SMono.justOrEmpty(tokenList.filter(row => row.id.equals(id)))
      .filter(seq => seq.nonEmpty)
      .doOnNext(seq => {
        tokenStore.put(username, tokenList diff seq)
      })
      .`then`
  }
}