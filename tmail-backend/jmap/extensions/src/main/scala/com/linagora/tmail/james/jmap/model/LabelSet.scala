package com.linagora.tmail.james.jmap.model

import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, SetError, UuidState}
import org.apache.james.jmap.mail.Keyword
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.JsObject

import scala.collection.Seq

case class LabelSetRequest(accountId: AccountId,
                           create: Option[Map[LabelCreationId, JsObject]],
                           destroy: Option[Seq[UnparsedLabelId]]) extends WithAccountId

case class LabelCreationId(id: Id)

case class LabelCreationResponse(id: LabelId,
                                 keyword: Keyword)

case class LabelSetResponse(accountId: AccountId,
                            oldState: Option[UuidState],
                            newState: UuidState,
                            created: Option[Map[LabelCreationId, LabelCreationResponse]],
                            notCreated: Option[Map[LabelCreationId, SetError]],
                            destroyed: Option[Seq[LabelId]],
                            notDestroyed: Option[Map[UnparsedLabelId, SetError]])