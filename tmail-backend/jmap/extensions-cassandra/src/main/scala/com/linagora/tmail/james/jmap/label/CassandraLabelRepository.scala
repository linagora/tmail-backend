package com.linagora.tmail.james.jmap.label

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelCreationRequest, LabelId, LabelNotFoundException}
import javax.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.core.Username
import org.apache.james.jmap.mail.Keyword
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class CassandraLabelRepository @Inject()(dao: CassandraLabelDAO) extends LabelRepository {
  override def addLabel(username: Username, labelCreationRequest: LabelCreationRequest): Publisher[Label] =
    dao.insert(username, labelCreationRequest.toLabel)

  override def addLabel(username: Username, label: Label): Publisher[Void] =
    dao.insert(username, label)
      .`then`()

  override def addLabels(username: Username, labelCreationRequests: java.util.Collection[LabelCreationRequest]): Publisher[Label] =
    SFlux.fromIterable(labelCreationRequests.asScala)
      .concatMap(addLabel(username, _))

  override def updateLabel(username: Username, labelId: LabelId, newDisplayName: Option[DisplayName], newColor: Option[Color]): Publisher[Void] =
    dao.selectOne(username, labelId.toKeyword)
      .switchIfEmpty(SMono.error(LabelNotFoundException(labelId)))
      .flatMap(_ => dao.updateLabel(username, labelId.toKeyword, newDisplayName, newColor))

  override def getLabels(username: Username, ids: java.util.Collection[LabelId]): Publisher[Label] =
    dao.selectSome(username = username,
      keywords = ids.stream()
        .map(_.toKeyword)
        .toList
        .asInstanceOf[java.util.Collection[Keyword]])

  override def listLabels(username: Username): Publisher[Label] =
    dao.selectAll(username)

  override def deleteLabel(username: Username, labelId: LabelId): Publisher[Void] =
    dao.deleteOne(username, labelId.toKeyword)

  override def deleteAllLabels(username: Username): Publisher[Void] =
    dao.deleteAll(username)
}

case class CassandraLabelRepositoryModule() extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder, classOf[CassandraModule])
      .addBinding().toInstance(CassandraLabelTable.MODULE)

    bind(classOf[CassandraLabelDAO]).in(Scopes.SINGLETON)

    bind(classOf[LabelRepository]).to(classOf[CassandraLabelRepository])
    bind(classOf[CassandraLabelRepository]).in(Scopes.SINGLETON)
  }
}
