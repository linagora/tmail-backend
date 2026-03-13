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

import java.util

import com.google.common.collect.{HashBasedTable, ImmutableList, Table, Tables}
import com.google.inject.name.Named
import com.linagora.tmail.james.jmap.model.{Label, LabelCreationRequest, LabelId, DisplayName, Color, DescriptionUpdate, LabelNotFoundException}
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.events.{Event, EventBus}
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.change.AccountIdRegistrationKey
import org.apache.james.jmap.mail.Keyword
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class MemoryLabelRepository @Inject()(@Named("TMAIL_EVENT_BUS") eventBus: EventBus) extends LabelRepository {
  private val labelsTable: Table[Username, Keyword, Label] = Tables.synchronizedTable(HashBasedTable.create())

  override def addLabel(username: Username, labelCreationRequest: LabelCreationRequest): Publisher[Label] = {
    SMono.fromCallable(() => {
      val label: Label = labelCreationRequest.toLabel
      labelsTable.put(username, label.keyword, label)
      label
    }).flatMap(label =>
      SMono.fromPublisher(eventBus.dispatch(LabelCreated(Event.EventId.random(), username, label), AccountIdRegistrationKey(AccountId.fromUsername(username))))
        .`then`(SMono.just(label)))
  }

  override def addLabel(username: Username, label: Label): Publisher[Void] =
    SMono.fromCallable(() => labelsTable.put(username, label.keyword, label))
      .`then`(SMono.fromPublisher(eventBus.dispatch(LabelCreated(Event.EventId.random(), username, label), AccountIdRegistrationKey(AccountId.fromUsername(username)))))
      .`then`()

  override def addLabels(username: Username, labelCreationRequests: util.Collection[LabelCreationRequest]): Publisher[Label] =
    SFlux.fromIterable(labelCreationRequests.asScala)
      .concatMap(creationRequest => addLabel(username, creationRequest))

  override def updateLabel(username: Username, labelId: LabelId, newDisplayName: Option[DisplayName] = None, newColor: Option[Color] = None, newDescription: Option[DescriptionUpdate] = None): Publisher[Void] =
    SMono.justOrEmpty(labelsTable.get(username, labelId.toKeyword))
      .switchIfEmpty(SMono.error(LabelNotFoundException(labelId)))
      .flatMap(oldLabel => {
        val updatedLabel = oldLabel.copy(
          displayName = newDisplayName.getOrElse(oldLabel.displayName),
          color = newColor.orElse(oldLabel.color),
          description = newDescription match {
            case Some(DescriptionUpdate(Some(desc))) => Some(desc)
            case Some(DescriptionUpdate(None)) => None
            case None => oldLabel.description
          }
        )
        labelsTable.put(username, labelId.toKeyword, updatedLabel)
        SMono.fromPublisher(eventBus.dispatch(LabelUpdated(Event.EventId.random(), username, updatedLabel), AccountIdRegistrationKey(AccountId.fromUsername(username))))
      })
      .`then`()

  override def getLabels(username: Username, ids: util.Collection[LabelId]): Publisher[Label] =
    SFlux.fromIterable(ImmutableList.copyOf(labelsTable.row(username).entrySet()).asScala)
      .filter((entry: util.Map.Entry[Keyword, Label]) => ids.contains(LabelId.fromKeyword(entry.getKey)))
      .map(_.getValue)

  override def listLabels(username: Username): Publisher[Label] =
    SFlux.fromIterable(ImmutableList.copyOf(labelsTable.row(username).entrySet()).asScala)
      .map(_.getValue)

  override def deleteLabel(username: Username, labelId: LabelId): Publisher[Void] =
    SMono.fromCallable(() => labelsTable.remove(username, labelId.toKeyword))
      .flatMap(_ => SMono.fromPublisher(eventBus.dispatch(LabelDestroyed(Event.EventId.random(), username, labelId), AccountIdRegistrationKey(AccountId.fromUsername(username)))))
      .`then`()

  override def deleteAllLabels(username: Username): Publisher[Void] =
    SMono.fromCallable(() => labelsTable.row(username).clear())
      .`then`()
<<<<<<< HEAD

  override def setLabelReadOnly(username: Username, labelId: LabelId, readOnly: Boolean): Publisher[Void] =
    SMono.justOrEmpty(labelsTable.get(username, labelId.toKeyword))
      .doOnNext(label => labelsTable.put(username, labelId.toKeyword, label.copy(readOnly = readOnly)))
      .switchIfEmpty(SMono.error(LabelNotFoundException(labelId)))
      .`then`()
}
=======
}
>>>>>>> 220bd3d04 (ISSUE-2255 Fire Label events)
