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

package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.json.ForwardSerializer
import com.linagora.tmail.james.jmap.method.AsEitherRequest
import org.apache.james.jmap.core.SetError.{SetErrorDescription, SetErrorType, invalidArgumentValue, invalidPatchValue, serverFailValue}
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.rrt.api.RecipientRewriteTableException
import play.api.libs.json.JsObject

case class ForwardSetRequest(accountId: AccountId,
                             update: Option[Map[String, ForwardSetPatchObject]],
                             create: Option[Map[String, JsObject]],
                             destroy: Option[Seq[String]]) extends WithAccountId {

  def parseUpdateRequest(): Map[String, Either[IllegalArgumentException, ForwardSetPatchObject]] =
    update.getOrElse(Map())
      .map({
        case (id, patch) if id.equals(ForwardId.asString) => (id, Right(patch))
        case (id, _) => (id, Left(new IllegalArgumentException(s"id $id must be ${ForwardId.asString}")))
      })
}

case class ForwardSetPatchObject(jsObject: JsObject) {
  def asForwardUpdateRequest: Either[Exception, ForwardUpdateRequest] =
    ForwardSerializer.deserializeForwardSetUpdateRequest(jsObject).asEitherRequest
}

case class ForwardSetError(`type`: SetErrorType, description: Option[SetErrorDescription])

case class ForwardSetResponse(accountId: AccountId,
                              newState: UuidState,
                              updated: Option[Map[String, ForwardSetUpdateResponse]],
                              notUpdated: Option[Map[String, ForwardSetError]],
                              notCreated: Option[Map[String, ForwardSetError]],
                              notDestroyed: Option[Map[String, ForwardSetError]])

case class ForwardSetUpdateResponse(value: JsObject)

object ForwardSetUpdateResults {
  def empty(): ForwardSetUpdateResults = ForwardSetUpdateResults(Map(), Map())

  def merge(a: ForwardSetUpdateResults, b: ForwardSetUpdateResults): ForwardSetUpdateResults = ForwardSetUpdateResults(a.updateSuccess ++ b.updateSuccess, a.updateFailures ++ b.updateFailures)
}

case class ForwardSetUpdateResults(updateSuccess: Map[String, ForwardSetUpdateResponse],
                                   updateFailures: Map[String, ForwardSetError])

sealed trait ForwardSetUpdateResult {
  def updated: Map[String, ForwardSetUpdateResponse]

  def notUpdated: Map[String, ForwardSetError]

  def asForwardSetUpdateResult: ForwardSetUpdateResults = ForwardSetUpdateResults(updated, notUpdated)
}

case object ForwardSetUpdateSuccess extends ForwardSetUpdateResult {
  override def updated: Map[String, ForwardSetUpdateResponse] = Map(ForwardId.asString -> ForwardSetUpdateResponse(JsObject(Seq())))

  override def notUpdated: Map[String, ForwardSetError] = Map()
}

case class ForwardSetUpdateFailure(id: String, exception: Throwable) extends ForwardSetUpdateResult {
  override def updated: Map[String, ForwardSetUpdateResponse] = Map()

  override def notUpdated: Map[String, ForwardSetError] = Map(id -> asSetError(exception))

  def asSetError(exception: Throwable): ForwardSetError = exception match {
    case e: IllegalArgumentException => ForwardSetError(invalidArgumentValue, Some(SetErrorDescription(e.getMessage)))
    case e: RecipientRewriteTableException => ForwardSetError(invalidPatchValue, Some(SetErrorDescription(e.getMessage)))
    case e: Throwable => ForwardSetError(serverFailValue, Some(SetErrorDescription(e.getMessage)))
  }
}

case class ForwardUpdateRequest(localCopy: LocalCopy,
                                forwards: Seq[Forward])


