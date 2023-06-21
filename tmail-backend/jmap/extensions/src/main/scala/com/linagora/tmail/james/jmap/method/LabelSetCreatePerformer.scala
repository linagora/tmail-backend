package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.LabelSerializer
import com.linagora.tmail.james.jmap.label.LabelRepository
import com.linagora.tmail.james.jmap.method.LabelSetCreatePerformer.{LabelCreationFailure, LabelCreationResult, LabelCreationResults, LabelCreationSuccess}
import com.linagora.tmail.james.jmap.model.{LabelCreationId, LabelCreationRequest, LabelCreationResponse, LabelSetRequest}
import javax.inject.Inject
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

case class LabelCreationParseException(setError: SetError) extends Exception

object LabelSetCreatePerformer {
  sealed trait LabelCreationResult {
    def labelCreationId: LabelCreationId
  }
  case class LabelCreationSuccess(labelCreationId: LabelCreationId, labelCreationResponse: LabelCreationResponse) extends LabelCreationResult
  case class LabelCreationFailure(labelCreationId: LabelCreationId, exception: Throwable) extends LabelCreationResult {
    def asLabelSetError: SetError = exception match {
      case e: LabelCreationParseException => e.setError
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class LabelCreationResults(created: Seq[LabelCreationResult]) {
    def retrieveCreated: Map[LabelCreationId, LabelCreationResponse] = created
      .flatMap(result => result match {
        case success: LabelCreationSuccess => Some(success.labelCreationId, success.labelCreationResponse)
        case _ => None
      })
      .toMap
      .map(creation => (creation._1, creation._2))

    def retrieveErrors: Map[LabelCreationId, SetError] = created
      .flatMap(result => result match {
        case failure: LabelCreationFailure => Some(failure.labelCreationId, failure.asLabelSetError)
        case _ => None
      })
      .toMap
  }
}

class LabelSetCreatePerformer @Inject()(val labelRepository: LabelRepository,
                                        val metricFactory: MetricFactory) {
  def createLabels(mailboxSession: MailboxSession,
                   labelSetRequest: LabelSetRequest): SMono[LabelCreationResults] =
    SFlux.fromIterable(labelSetRequest.create
      .getOrElse(Map.empty))
      .concatMap {
        case (clientId, json) => parseCreate(json)
          .fold(e => SMono.just[LabelCreationResult](LabelCreationFailure(clientId, e)),
            creationRequest => createLabel(mailboxSession, clientId, creationRequest))
      }.collectSeq()
      .map(LabelCreationResults)

  private def parseCreate(jsObject: JsObject): Either[LabelCreationParseException, LabelCreationRequest] =
    LabelCreationRequest.validateProperties(jsObject)
      .flatMap(validJsObject => Json.fromJson(validJsObject)(LabelSerializer.labelCreationRequest) match {
        case JsSuccess(creationRequest, _) => Right(creationRequest)
        case JsError(errors) => Left(LabelCreationParseException(standardError(errors)))
      })

  private def createLabel(mailboxSession: MailboxSession,
                          clientId: LabelCreationId,
                          labelCreationRequest: LabelCreationRequest): SMono[LabelCreationResult] =
    SMono.fromPublisher(labelRepository.addLabel(mailboxSession.getUser, labelCreationRequest))
      .map(label => LabelCreationSuccess(clientId, LabelCreationResponse(label.id, label.keyword)))
      .onErrorResume(e => SMono.just[LabelCreationResult](LabelCreationFailure(clientId, e)))

}
