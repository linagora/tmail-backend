package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.firebase.{FirebasePushClient, FirebaseSubscriptionRepository}
import com.linagora.tmail.james.jmap.json.FirebaseSubscriptionSerializer
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionSetCreatePerformer.{CreationFailure, CreationResult, CreationResults, CreationSuccess, LOGGER}
import com.linagora.tmail.james.jmap.model.{DeviceClientIdInvalidException, ExpireTimeInvalidException, FirebaseSubscriptionCreation, FirebaseSubscriptionCreationId, FirebaseSubscriptionCreationParseException, FirebaseSubscriptionCreationRequest, FirebaseSubscriptionCreationResponse, FirebaseSubscriptionExpiredTime, FirebaseSubscriptionSetRequest, TokenInvalidException}
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}
import javax.inject.Inject


object FirebaseSubscriptionSetCreatePerformer {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[FirebaseSubscriptionSetCreatePerformer])

  trait CreationResult

  case class CreationSuccess(clientId: FirebaseSubscriptionCreationId, response: FirebaseSubscriptionCreationResponse) extends CreationResult

  case class CreationFailure(clientId: FirebaseSubscriptionCreationId, e: Throwable) extends CreationResult {
    def asMessageSetError: SetError = e match {
      case e: FirebaseSubscriptionCreationParseException => e.setError
      case e: ExpireTimeInvalidException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("expires")))
      case e: DeviceClientIdInvalidException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("deviceClientId")))
      case e: TokenInvalidException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("token")))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case _ => {
        LOGGER.warn("Could not create firebase subscription request", e)
        SetError.serverFail(SetErrorDescription(e.getMessage))
      }
    }
  }

  case class CreationResults(results: Seq[CreationResult]) {
    def created: Option[Map[FirebaseSubscriptionCreationId, FirebaseSubscriptionCreationResponse]] =
      Option(results.flatMap {
        case result: CreationSuccess => Some((result.clientId, result.response))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)

    def notCreated: Option[Map[FirebaseSubscriptionCreationId, SetError]] = {
      Option(results.flatMap {
        case failure: CreationFailure => Some((failure.clientId, failure.asMessageSetError))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)
    }
  }
}

class FirebaseSubscriptionSetCreatePerformer @Inject()(val repository: FirebaseSubscriptionRepository,
                                                       val serializer: FirebaseSubscriptionSerializer,
                                                       val firebaseClient: FirebasePushClient) {

  def create(request: FirebaseSubscriptionSetRequest, username: Username): SMono[CreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map()))
      .concatMap {
        case (clientId, json) => parseCreate(json)
          .fold(e => SMono.just[CreationResult](CreationFailure(clientId, e)),
            creationRequest => create(clientId, creationRequest, username))
      }.collectSeq()
      .map(CreationResults)


  private def parseCreate(json: JsObject): Either[Exception, FirebaseSubscriptionCreationRequest] = for {
    validJsObject <- FirebaseSubscriptionCreation.validateProperties(json)
    parsedRequest <- serializer.deserializeFirebaseSubscriptionCreationRequest(validJsObject).asEither
      .left.map(errors => FirebaseSubscriptionCreationParseException.from(errors))
    validatedRequest <- parsedRequest.validate
      .left.map(e => FirebaseSubscriptionCreationParseException(SetError.invalidArguments(SetErrorDescription(e.getMessage))))
  } yield validatedRequest

  private def create(clientId: FirebaseSubscriptionCreationId, request: FirebaseSubscriptionCreationRequest, username: Username): SMono[CreationResult] =
    SMono.fromPublisher(firebaseClient.validateToken(request.token))
      .onErrorResume(e => {
        LOGGER.warn("Failure validating FCM token", e)
        SMono.just(java.lang.Boolean.TRUE)
      })
      .flatMap(isValid => if (isValid) {
        SMono.fromPublisher(repository.save(username, request))
          .map(subscription => CreationSuccess(clientId, FirebaseSubscriptionCreationResponse(subscription.id, showExpires(subscription.expires, request))))
          .onErrorResume(e => SMono.just[CreationResult](CreationFailure(clientId, e)))
      } else {
        SMono.just[CreationResult](CreationFailure(clientId, TokenInvalidException("Token is not valid")))
      })

  private def showExpires(expires: FirebaseSubscriptionExpiredTime, request: FirebaseSubscriptionCreationRequest): Option[FirebaseSubscriptionExpiredTime] = request.expires match {
    case Some(requestExpires) if expires.value.eq(requestExpires.value) => None
    case _ => Some(expires)
  }
}