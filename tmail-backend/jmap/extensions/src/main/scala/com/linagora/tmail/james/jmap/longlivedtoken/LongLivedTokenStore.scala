package com.linagora.tmail.james.jmap.longlivedtoken

import com.google.common.base.Preconditions
import org.apache.commons.lang3.tuple.ImmutableTriple
import org.apache.james.core.Username
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

case class DeviceId(value: String) extends AnyVal

case class LongLivedTokenId(value: UUID)

case class LongLivedTokenSecret(value: UUID)

case class LongLivedToken(deviceId: DeviceId, secret: LongLivedTokenSecret)

case class LongLivedTokenFootPrint(id: LongLivedTokenId, deviceId: DeviceId)

case class LongLivedTokenNotFoundException() extends RuntimeException

trait LongLivedTokenStore {
  def store(username: Username, longLivedToken: LongLivedToken): Publisher[LongLivedTokenId]

  def validate(username: Username, secret: LongLivedTokenSecret): Publisher[LongLivedTokenFootPrint]

  def listTokens(user: Username): Publisher[LongLivedTokenFootPrint]

  def revoke(username: Username, id: LongLivedTokenFootPrint): Publisher[Unit]
}

class InMemoryLongLivedTokenStore(tokenStore: Map[Username, Seq[ImmutableTriple[DeviceId, LongLivedTokenId, LongLivedTokenSecret]]] = scala.collection.concurrent.TrieMap())
  extends LongLivedTokenStore {

  override def store(username: Username, longLivedToken: LongLivedToken): Publisher[LongLivedTokenId] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(longLivedToken)
    SMono.just(tokenStore.contains(username))
      .filter(userExists => userExists)
      .map(_ => storeWhenUserExists(username, longLivedToken))
      .switchIfEmpty(storeWhenUserNotExists(username, longLivedToken))
  }

  private def storeWhenUserExists(username: Username, longLivedToken: LongLivedToken): LongLivedTokenId = {
    val tokenList: Seq[ImmutableTriple[DeviceId, LongLivedTokenId, LongLivedTokenSecret]] = tokenStore(username)
    tokenList
      .find(triple => triple.left.equals(longLivedToken.deviceId) && triple.right.equals(longLivedToken.secret))
      .map(e => e.middle)
      .getOrElse({
        val longLivedTokenId: LongLivedTokenId = LongLivedTokenId.generate
        tokenStore.put(username, tokenList :+ ImmutableTriple.of(longLivedToken.deviceId, longLivedTokenId, longLivedToken.secret))
        longLivedTokenId
      })
  }

  private def storeWhenUserNotExists(username: Username, longLivedToken: LongLivedToken): SMono[LongLivedTokenId] =
    SMono.fromCallable(() => {
      val longLivedTokenId: LongLivedTokenId = LongLivedTokenId.generate
      tokenStore.put(username, Seq(ImmutableTriple.of(longLivedToken.deviceId, longLivedTokenId, longLivedToken.secret)))
      longLivedTokenId
    })

  override def validate(username: Username, secret: LongLivedTokenSecret): Publisher[LongLivedTokenFootPrint] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(secret)
    SFlux.fromIterable(tokenStore.getOrElse(username, Seq.empty))
      .filter(row => row.right.equals(secret))
      .head
      .map(tripe => LongLivedTokenFootPrint(tripe.middle, tripe.left))
      .switchIfEmpty(SMono.error(new LongLivedTokenNotFoundException))
  }

  override def listTokens(username: Username): Publisher[LongLivedTokenFootPrint] = {
    Preconditions.checkNotNull(username)
    SFlux.fromIterable(tokenStore.getOrElse(username, Seq.empty))
      .map(triple => LongLivedTokenFootPrint(triple.middle, triple.left))
  }

  override def revoke(username: Username, id: LongLivedTokenFootPrint): Publisher[Unit] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(id)
    val tokenList: Seq[ImmutableTriple[DeviceId, LongLivedTokenId, LongLivedTokenSecret]] = tokenStore.getOrElse(username, Seq.empty)

    SMono.justOrEmpty(tokenList.filter(row => row.middle.equals(id.id) && row.left.equals(id.deviceId)))
      .filter(seq => seq.nonEmpty)
      .doOnNext(seq => {
        tokenStore.put(username, tokenList diff seq)
      })
      .switchIfEmpty(SMono.error(new LongLivedTokenNotFoundException))
      .`then`
  }
}