package com.linagora.tmail.james.jmap.model

import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, SetError, UuidState}
import org.apache.james.jmap.mail.Keyword
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.JsObject

case class LabelSetRequest(accountId: AccountId,
                           create: Option[Map[LabelCreationId, JsObject]]) extends WithAccountId

case class LabelCreationId(id: Id)

case class LabelCreationResponse(id: LabelId,
                                 keyword: Keyword)

case class LabelSetResponse(accountId: AccountId,
                            oldState: Option[UuidState],
                            newState: UuidState,
                            created: Option[Map[LabelCreationId, LabelCreationResponse]],
                            notCreated: Option[Map[LabelCreationId, SetError]])