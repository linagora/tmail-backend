package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionRepository
import com.linagora.tmail.james.jmap.model.{FirebaseSubscriptionExpiredTime, FirebaseSubscriptionId, FirebaseSubscriptionPatchObject, FirebaseSubscriptionSetRequest, FirebaseSubscriptionUpdateFailure, FirebaseSubscriptionUpdateResult, FirebaseSubscriptionUpdateResults, FirebaseSubscriptionUpdateSuccess, UnparsedFirebaseSubscriptionId, ValidatedFirebaseSubscriptionPatchObject}
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.util.ReactorUtils
import reactor.core.scala.publisher.{SFlux, SMono}

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
    SFlux.concat(updateTypes(username, id, validatedPatch.typeUpdate),
      updateExpires(username, id, validatedPatch.expiresUpdate))
      .last()

  private def updateTypes(username: Username, id: FirebaseSubscriptionId, typeUpdate: Option[Set[TypeName]]): SMono[FirebaseSubscriptionUpdateResult] =
    SMono.justOrEmpty(typeUpdate)
      .flatMap(typesUpdate => SMono(repository.updateTypes(username, id, typesUpdate.asJava)))
      .`then`(SMono.just(FirebaseSubscriptionUpdateSuccess(id)))

  private def updateExpires(username: Username, id: FirebaseSubscriptionId, expiresRequest: Option[FirebaseSubscriptionExpiredTime]): SMono[FirebaseSubscriptionUpdateResult] =
    SMono.justOrEmpty(expiresRequest)
      .flatMap(expires => SMono(repository.updateExpireTime(username, id, expires.value))
        .map(expiresResult => FirebaseSubscriptionUpdateSuccess(id = id, evaluateExpiresInResponse(expires, expiresResult))))

  private def evaluateExpiresInResponse(expiresRequest: FirebaseSubscriptionExpiredTime, expiresResult: FirebaseSubscriptionExpiredTime): Option[FirebaseSubscriptionExpiredTime] =
    if (expiresRequest.equals(expiresResult)) {
      None
    } else {
      Some(expiresResult)
    }
}