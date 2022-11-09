package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionRepository
import com.linagora.tmail.james.jmap.model.{FirebaseSubscriptionId, FirebaseSubscriptionPatchObject, FirebaseSubscriptionSetRequest, FirebaseSubscriptionUpdateFailure, FirebaseSubscriptionUpdateResult, FirebaseSubscriptionUpdateResults, FirebaseSubscriptionUpdateSuccess, UnparsedFirebaseSubscriptionId, ValidatedFirebaseSubscriptionPatchObject}
import org.apache.james.core.Username
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.util.ReactorUtils
import reactor.core.scala.publisher.{SFlux, SMono}

import javax.inject.Inject
import scala.jdk.CollectionConverters._


class FirebaseSubscriptionSetUpdatePerformer @Inject()(val repository: FirebaseSubscriptionRepository,
                                                       val typeStateFactory: TypeStateFactory) {

  def update(request: FirebaseSubscriptionSetRequest, username: Username): SMono[FirebaseSubscriptionUpdateResults] = {
    SFlux.fromIterable(request.update.getOrElse(Map()))
      .flatMap({
        case (unparsedId: UnparsedFirebaseSubscriptionId, patch: FirebaseSubscriptionPatchObject) =>
          val either = for {
            id <- FirebaseSubscriptionId.liftOrThrow(unparsedId)
            validatedPatch <- patch.validate(typeStateFactory)
          } yield {
            updateSubscription(username, id, validatedPatch)
          }
          either.fold(e => SMono.just(FirebaseSubscriptionUpdateFailure(unparsedId, e)),
            sMono => sMono
              .onErrorResume(e => SMono.just(FirebaseSubscriptionUpdateFailure(unparsedId, e))))
      }, maxConcurrency = ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(FirebaseSubscriptionUpdateResults)
  }

  private def updateSubscription(username: Username, id: FirebaseSubscriptionId, validatedPatch: ValidatedFirebaseSubscriptionPatchObject): SMono[FirebaseSubscriptionUpdateResult] =
    SMono.justOrEmpty(validatedPatch.typeUpdate)
      .flatMap(typesUpdate => SMono(repository.updateTypes(username, id, typesUpdate.asJava)))
      .`then`(SMono.just(FirebaseSubscriptionUpdateSuccess(id)))

}