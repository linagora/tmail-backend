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

package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.LabelSerializer
import com.linagora.tmail.james.jmap.label.LabelRepository
import com.linagora.tmail.james.jmap.method.LabelSetCreatePerformer.{LabelCreationFailure, LabelCreationResult, LabelCreationResults, LabelCreationSuccess}
import com.linagora.tmail.james.jmap.model.{LabelCreationId, LabelCreationParseException, LabelCreationRequest, LabelCreationResponse, LabelSetRequest}
import jakarta.inject.Inject
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsObject, JsPath, JsSuccess, Json, JsonValidationError}
import reactor.core.scala.publisher.{SFlux, SMono}

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
    LabelSerializer.deserializeLabelCreationRequest(jsObject) match {
        case JsSuccess(creationRequest, _) => Right(creationRequest)
        case JsError(errors) => Left(LabelCreationParseException(standardError(errors)))
    }

  private def createLabel(mailboxSession: MailboxSession,
                          clientId: LabelCreationId,
                          labelCreationRequest: LabelCreationRequest): SMono[LabelCreationResult] =
    SMono.fromPublisher(labelRepository.addLabel(mailboxSession.getUser, labelCreationRequest))
      .map(label => LabelCreationSuccess(clientId, LabelCreationResponse(label.id, label.keyword)))
      .onErrorResume(e => SMono.just[LabelCreationResult](LabelCreationFailure(clientId, e)))

}
