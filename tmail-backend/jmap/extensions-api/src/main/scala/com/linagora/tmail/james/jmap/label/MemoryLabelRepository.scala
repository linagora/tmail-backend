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

import com.google.common.collect.{HashBasedTable, ImmutableList, ImmutableSet, Table, Tables}
import com.google.inject.name.Named
import com.linagora.tmail.james.jmap.model.{Color, DescriptionUpdate, DisplayName, Label, LabelCreationRequest, LabelId, LabelNotFoundException}
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.events.{Event, EventBus, RegistrationKey}
import org.apache.james.jmap.mail.Keyword
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class MemoryLabelRepository @Inject()(@Named("TMAIL_EVENT_BUS") eventBus: EventBus) extends LabelRepository {
  private val labelsTable: Table[Username, Keyword, Label] = Tables.synchronizedTable(HashBasedTable.create())
  private val NO_REGISTRATION_KEYS: util.Set[RegistrationKey] = ImmutableSet.of

  override def addLabel(username: Username, labelCreationRequest: LabelCreationRequest): Publisher[Label] = {
    SMono.fromCallable(() => {
      val label: Label = labelCreationRequest.toLabel
      labelsTable.put(username, label.keyword, label)
      label
    }).flatMap(label =>
      dispatch(LabelCreated(Event.EventId.random(), username, label))
        .`then`(SMono.just(label)))
  }

  override def addLabel(username: Username, label: Label): Publisher[Void] =
    SMono.fromCallable(() => labelsTable.put(username, label.keyword, label))
      .`then`(dispatch(LabelCreated(Event.EventId.random(), username, label)))
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
        dispatch(LabelUpdated(Event.EventId.random(), username, updatedLabel))
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
      .flatMap(_ => dispatch(LabelDestroyed(Event.EventId.random(), username, labelId)))
      .`then`()

  override def deleteAllLabels(username: Username): Publisher[Void] =
    SMono.fromCallable(() => ImmutableList.copyOf(labelsTable.row(username).keySet()))
      .flatMapMany(keywords => SFlux.fromIterable(keywords.asScala))
      .concatMap(keyword => deleteLabel(username, LabelId.fromKeyword(keyword)))
      .`then`()

  override def setLabelReadOnly(username: Username, labelId: LabelId, readOnly: Boolean): Publisher[Void] =
    SMono.justOrEmpty(labelsTable.get(username, labelId.toKeyword))
      .switchIfEmpty(SMono.error(LabelNotFoundException(labelId)))
      .flatMap(label => {
        val updatedLabel = label.copy(readOnly = readOnly)
        labelsTable.put(username, labelId.toKeyword, updatedLabel)
        dispatch(LabelUpdated(Event.EventId.random(), username, updatedLabel))
      })
      .`then`()

  private def dispatch(event: TmailLabelEvent): SMono[Void] =
    SMono.fromPublisher(eventBus.dispatch(event, NO_REGISTRATION_KEYS))
}
