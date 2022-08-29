package com.linagora.tmail.james.jmap.method

import java.nio.charset.StandardCharsets

import com.linagora.tmail.encrypted.KeystoreManager
import com.linagora.tmail.james.jmap.json.KeystoreSerializer
import com.linagora.tmail.james.jmap.method.KeystoreSetCreatePerformer.{KeystoreCreationFailure, KeystoreCreationResult, KeystoreCreationResults, KeystoreCreationSuccess}
import com.linagora.tmail.james.jmap.model.{KeystoreCreationId, KeystoreCreationRequest, KeystoreCreationResponse, KeystoreSetRequest}
import javax.inject.Inject
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsObject, JsPath, JsSuccess, Json, JsonValidationError}
import reactor.core.scala.publisher.{SFlux, SMono}

object KeystoreSetCreatePerformer {
  sealed trait KeystoreCreationResult {
    def keystoreCreationId: KeystoreCreationId
  }
  case class KeystoreCreationSuccess(keystoreCreationId: KeystoreCreationId, keystoreCreationResponse: KeystoreCreationResponse) extends KeystoreCreationResult
  case class KeystoreCreationFailure(keystoreCreationId: KeystoreCreationId, exception: Throwable) extends KeystoreCreationResult {
    def asKeystoreSetError: SetError = exception match {
      case e: KeystoreCreationParseException => e.setError
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class KeystoreCreationResults(created: Seq[KeystoreCreationResult]) {
    def retrieveCreated: Map[KeystoreCreationId, KeystoreCreationResponse] = created
      .flatMap(result => result match {
        case success: KeystoreCreationSuccess => Some(success.keystoreCreationId, success.keystoreCreationResponse)
        case _ => None
      })
      .toMap
      .map(creation => (creation._1, creation._2))

    def retrieveErrors: Map[KeystoreCreationId, SetError] = created
      .flatMap(result => result match {
        case failure: KeystoreCreationFailure => Some(failure.keystoreCreationId, failure.asKeystoreSetError)
        case _ => None
      })
      .toMap
  }
}

class KeystoreSetCreatePerformer @Inject()(serializer: KeystoreSerializer,
                                           keystore: KeystoreManager,
                                           val metricFactory: MetricFactory,
                                           val sessionSupplier: SessionSupplier) {

  def createKeys(mailboxSession: MailboxSession,
                 keystoreSetRequest: KeystoreSetRequest): SMono[KeystoreCreationResults] =
    SFlux.fromIterable(keystoreSetRequest.create
        .getOrElse(Map.empty))
      .concatMap {
        case (clientId, json) => parseCreate(json)
          .fold(e => SMono.just[KeystoreCreationResult](KeystoreCreationFailure(clientId, e)),
            creationRequest => createKey(mailboxSession, clientId, creationRequest))
      }.collectSeq()
      .map(KeystoreCreationResults)

  private def parseCreate(jsObject: JsObject): Either[KeystoreCreationParseException, KeystoreCreationRequest] =
    KeystoreCreationRequest.validateProperties(jsObject)
      .flatMap(validJsObject => Json.fromJson(validJsObject)(serializer.keystoreCreationRequest) match {
        case JsSuccess(creationRequest, _) => Right(creationRequest)
        case JsError(errors) => Left(KeystoreCreationParseException(keystoreSetError(errors)))
      })

  private def keystoreSetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError =
    errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in Keystore object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in Keystore object"))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in Keystore object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    }

  private def createKey(mailboxSession: MailboxSession,
                        clientId: KeystoreCreationId,
                        keystoreCreationRequest: KeystoreCreationRequest): SMono[KeystoreCreationResult] =
      SMono.fromPublisher(keystore.save(mailboxSession.getUser, keystoreCreationRequest.key.value.getBytes(StandardCharsets.UTF_8)))
        .map(keyId => KeystoreCreationSuccess(clientId, KeystoreCreationResponse(keyId)))
        .onErrorResume(e => SMono.just[KeystoreCreationResult](KeystoreCreationFailure(clientId, e)))
}
