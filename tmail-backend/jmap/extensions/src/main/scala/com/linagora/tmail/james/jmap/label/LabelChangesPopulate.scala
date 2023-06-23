package com.linagora.tmail.james.jmap.label

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.method.LabelSetCreatePerformer.LabelCreationResults
import com.linagora.tmail.james.jmap.method.LabelSetDeletePerformer.LabelDeletionResults
import com.linagora.tmail.james.jmap.method.LabelUpdateResults
import com.linagora.tmail.james.jmap.model.LabelId
import org.apache.james.core.Username
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import reactor.core.scala.publisher.SMono

class LabelChangesPopulate @Inject()(val labelChangeRepository: LabelChangeRepository,
                                     val stateFactory: State.Factory) {
  def populate(username: Username, createdResults: LabelCreationResults, destroyResults: LabelDeletionResults, updatedResults: LabelUpdateResults): SMono[Unit] = {
    val creationIds: Set[LabelId] = createdResults.retrieveCreated.map(creation => creation._2.id).toSet
    val updateIds: Set[LabelId] = updatedResults.updated.keySet
    val destroyIds: Set[LabelId] = destroyResults.destroyed.toSet
    if (creationIds.isEmpty && updateIds.isEmpty && destroyIds.isEmpty) {
      SMono.empty
    } else {
      SMono(labelChangeRepository.save(
        LabelChange(
          accountId = AccountId.fromUsername(username),
          state = stateFactory.generate(),
          created = creationIds,
          updated = updateIds,
          destroyed = destroyIds))).`then`()
    }
  }
}