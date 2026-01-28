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

package com.linagora.tmail.james.jmap.label

import java.io.FileNotFoundException

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Provides, Scopes}
import com.linagora.tmail.james.jmap.model.{Color, DescriptionUpdate, DisplayName, Label, LabelCreationRequest, LabelId, LabelNotFoundException}
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraDataDefinition
import org.apache.james.core.Username
import org.apache.james.jmap.mail.Keyword
import org.apache.james.user.api.{DeleteUserDataTaskStep, UsernameChangeTaskStep}
import org.apache.james.utils.PropertiesProvider
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters.CollectionHasAsScala



class CassandraLabelRepository @Inject()(dao: CassandraLabelDAO) extends LabelRepository {
  override def addLabel(username: Username, labelCreationRequest: LabelCreationRequest): Publisher[Label] =
    dao.insert(username, labelCreationRequest.toLabel)

  override def addLabel(username: Username, label: Label): Publisher[Void] =
    dao.insert(username, label)
      .`then`()

  override def addLabels(username: Username, labelCreationRequests: java.util.Collection[LabelCreationRequest]): Publisher[Label] =
    SFlux.fromIterable(labelCreationRequests.asScala)
      .concatMap(addLabel(username, _))

  override def updateLabel(username: Username, labelId: LabelId, newDisplayName: Option[DisplayName], newColor: Option[Color], newDescription: Option[DescriptionUpdate]): Publisher[Void] =
    dao.selectOne(username, labelId.toKeyword)
      .switchIfEmpty(SMono.error(LabelNotFoundException(labelId)))
      .flatMap(_ => dao.updateLabel(username, labelId.toKeyword, newDisplayName, newColor, newDescription))

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
    Multibinder.newSetBinder(binder, classOf[CassandraDataDefinition])
      .addBinding().toInstance(CassandraLabelTable.MODULE)
    Multibinder.newSetBinder(binder, classOf[CassandraDataDefinition])
      .addBinding().toInstance(CassandraLabelChangeTable.MODULE)

    bind(classOf[CassandraLabelDAO]).in(Scopes.SINGLETON)

    bind(classOf[LabelRepository]).to(classOf[CassandraLabelRepository])
    bind(classOf[CassandraLabelRepository]).in(Scopes.SINGLETON)

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[LabelUsernameChangeTaskStep])

    Multibinder.newSetBinder(binder(), classOf[DeleteUserDataTaskStep])
      .addBinding()
      .to(classOf[LabelUserDeletionTaskStep])

    bind(classOf[LabelChangeRepository]).to(classOf[CassandraLabelChangeRepository])
    bind(classOf[CassandraLabelChangeRepository]).in(Scopes.SINGLETON)
  }

  @Provides
  def provideCassandraLabelChangesConfiguration(propertiesProvider: PropertiesProvider): CassandraLabelChangesConfiguration = {
    try {
      CassandraLabelChangesConfiguration.from(propertiesProvider.getConfiguration("cassandra"))
    } catch {
      case _: FileNotFoundException => CassandraLabelChangesConfiguration.DEFAULT
    }
  }
}
