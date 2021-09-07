package com.linagora.tmail.james.jmap.ticket

import com.google.common.base.Preconditions
import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.longlivedtoken.{LongLivedToken, LongLivedTokenFootPrint, LongLivedTokenId, LongLivedTokenNotFoundException, LongLivedTokenSecret, LongLivedTokenStore}
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.core.Username
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import javax.inject.Inject

case class CassandraLongLivedTokenStoreModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CassandraLongLivedTokenStore]).in(Scopes.SINGLETON)
    bind(classOf[LongLivedTokenStore]).to(classOf[CassandraLongLivedTokenStore])

    Multibinder.newSetBinder(binder, classOf[CassandraModule])
      .addBinding().toInstance(LongLivedTokenStoreTable.module)

    bind(classOf[CassandraLongLivedTokenDAO]).in(Scopes.SINGLETON)
  }
}

class CassandraLongLivedTokenStore @Inject()(longLivedTokenDAO: CassandraLongLivedTokenDAO) extends LongLivedTokenStore {

  override def store(username: Username, longLivedToken: LongLivedToken): Publisher[LongLivedTokenId] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(longLivedToken)
    longLivedTokenDAO.insert(username, longLivedToken)
  }

  override def validate(username: Username, secret: LongLivedTokenSecret): Publisher[LongLivedTokenFootPrint] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(secret)
    longLivedTokenDAO.validate(username, secret)
      .switchIfEmpty(SMono.error(new LongLivedTokenNotFoundException))
  }

  override def listTokens(username: Username): Publisher[LongLivedTokenFootPrint] = {
    Preconditions.checkNotNull(username)
    longLivedTokenDAO.list(username)
  }

  override def revoke(username: Username, id: LongLivedTokenId): Publisher[Unit] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(id)
    longLivedTokenDAO.delete(username, id)
  }
}
