package com.linagora.tmail.james.jmap.label

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.method.LabelSetCreatePerformer.LabelCreationResults
import com.linagora.tmail.james.jmap.method.LabelSetDeletePerformer.LabelDeletionResults
import com.linagora.tmail.james.jmap.method.LabelUpdateResults
import com.linagora.tmail.james.jmap.model.LabelId
import jakarta.inject.Named
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.events.EventBus
import org.apache.james.jmap.InjectionKeys
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.change.{AccountIdRegistrationKey, StateChangeEvent}
import org.apache.james.jmap.core.UuidState
import reactor.core.scala.publisher.SMono

class LabelChangesPopulate @Inject()(@Named(InjectionKeys.JMAP) eventBus: EventBus,
                                     val labelChangeRepository: LabelChangeRepository,
                                     val stateFactory: State.Factory) {
  def populate(username: Username, createdResults: LabelCreationResults, destroyResults: LabelDeletionResults, updatedResults: LabelUpdateResults): SMono[UuidState] = {
    val creationIds: Set[LabelId] = createdResults.retrieveCreated.map(creation => creation._2.id).toSet
    val updateIds: Set[LabelId] = updatedResults.updated.keySet
    val destroyIds: Set[LabelId] = destroyResults.destroyed.toSet
    if (creationIds.isEmpty && updateIds.isEmpty && destroyIds.isEmpty) {
      SMono.empty
    } else {
      val state = stateFactory.generate()
      saveChangesAndDispatchEvent(username,
        LabelChange(accountId = AccountId.fromUsername(username),
          state = state,
          created = creationIds,
          updated = updateIds,
          destroyed = destroyIds))
        .`then`(SMono.just(UuidState.fromJava(state)))
    }
  }

  private def saveChangesAndDispatchEvent(username: Username, change: LabelChange): SMono[Unit] =
    SMono(labelChangeRepository.save(change))
      .`then`(SMono(eventBus.dispatch(toStateChangeEvent(username, change),
        AccountIdRegistrationKey(change.getAccountId)))).`then`()

  private def toStateChangeEvent(username: Username, change: LabelChange): StateChangeEvent =
    StateChangeEvent(
      eventId = EventId.random(),
      username = username,
      map = Map(LabelTypeName -> UuidState.fromJava(change.state)))

}
