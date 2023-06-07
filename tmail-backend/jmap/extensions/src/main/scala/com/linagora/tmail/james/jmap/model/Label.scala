package com.linagora.tmail.james.jmap.model

import java.util.UUID

import org.apache.james.jmap.core.Id
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.mail.Keyword

object LabelId {
  def fromKeyword(keyword: Keyword): LabelId =
    LabelId(Id.validate(keyword.flagName).toOption.get)

  def generate(): LabelId =
    LabelId(Id.validate(UUID.randomUUID().toString).toOption.get)
}

case class LabelId(id: Id) {
  def toKeyword: Keyword =
    Keyword.of(id.value).get
}

object KeywordUtil {
  def generate(): Keyword =
    Keyword.of(UUID.randomUUID().toString).get
}

case class DisplayName(value: String)

case class Color(value: String)

case class LabelCreationRequest(displayName: DisplayName, color: Option[Color]) {
  def toLabel: Label = {
    val keyword: Keyword = KeywordUtil.generate()

    Label(id = LabelId.fromKeyword(keyword),
      displayName = displayName,
      keyword = keyword,
      color = color)
  }
}

case class Label(id: LabelId, displayName: DisplayName, keyword: Keyword, color: Option[Color]) {
  def update(newDisplayName: Option[DisplayName], newColor: Option[Color]): Label =
    copy(displayName = newDisplayName.getOrElse(displayName),
      color = newColor.orElse(color))
}

case class LabelNotFoundException(id: LabelId) extends RuntimeException
