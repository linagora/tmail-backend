package com.linagora.tmail.james.jmap.model

import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.mail.Keyword

case class LabelId(id: Id)

case class DisplayName(value: String)

case class Color(value: String)

case class LabelCreationRequest(displayName: DisplayName, color: Option[Color])

case class Label(id: LabelId, displayName: DisplayName, keyword: Keyword, color: Option[Color])
