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

import com.linagora.tmail.james.jmap.label.LabelRepository
import com.linagora.tmail.james.jmap.model.{LabelId, LabelNotFoundException, LabelPatchObject, LabelPatchUpdateValidationException, LabelSetRequest, LabelUpdateResponse, UnparsedLabelId, ValidatedLabelPatchObject}
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.util.ReactorUtils
import reactor.core.scala.publisher.{SFlux, SMono}

sealed trait LabelUpdateResult

case class LabelUpdateFailure(id: UnparsedLabelId, exception: Throwable) extends LabelUpdateResult {
  def asSetError: SetError = exception match {
    case e: LabelPatchUpdateValidationException => SetError.invalidArguments(SetErrorDescription(e.error), e.asProperty)
    case e: LabelNotFoundException => SetError.notFound(SetErrorDescription(e.getMessage))
    case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage), None)
    case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
  }
}

case class LabelUpdateSuccess(id: LabelId) extends LabelUpdateResult

case class LabelUpdateResults(results: Seq[LabelUpdateResult]) {
  def updated: Map[LabelId, LabelUpdateResponse] =
    results.flatMap(result => result match {
      case success: LabelUpdateSuccess => Some((success.id, LabelUpdateResponse()))
      case _ => None
    }).toMap

  def notUpdated: Map[UnparsedLabelId, SetError] =
    results.flatMap(result => result match {
      case failure: LabelUpdateFailure => Some(failure.id, failure.asSetError)
      case _ => None
    }).toMap
}

class LabelSetUpdatePerformer @Inject()(val labelRepository: LabelRepository,
                                        val stateFactory : State.Factory) {
  def update(request: LabelSetRequest, username: Username): SMono[LabelUpdateResults] =
    SFlux.fromIterable(request.update.getOrElse(Map()))
      .flatMap({
        case (unparsedId: UnparsedLabelId, patch: LabelPatchObject) =>
          val either = for {
            validatedPatch <- patch.validate()
          } yield {
            updateLabel(username, LabelId(unparsedId.id), validatedPatch)
          }
          either.fold(e => SMono.just(LabelUpdateFailure(unparsedId, e)),
            sMono => sMono
              .onErrorResume(e => SMono.just(LabelUpdateFailure(unparsedId, e))))
      }, maxConcurrency = ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(LabelUpdateResults)

  private def updateLabel(username: Username, labelId: LabelId, validatedPatch: ValidatedLabelPatchObject): SMono[LabelUpdateResult] =
    SMono.fromPublisher(labelRepository.updateLabel(username, labelId, validatedPatch.displayNameUpdate, validatedPatch.colorUpdate, validatedPatch.documentationUpdate))
      .`then`(SMono.just(LabelUpdateSuccess(labelId)))
}