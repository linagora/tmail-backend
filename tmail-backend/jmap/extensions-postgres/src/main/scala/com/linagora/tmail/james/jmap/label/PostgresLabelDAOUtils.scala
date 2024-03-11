package com.linagora.tmail.james.jmap.label

import com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelTable.{COLOR, DISPLAY_NAME, KEYWORD}
import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelId}
import org.apache.james.jmap.mail.Keyword
import org.jooq.Record

object PostgresLabelDAOUtils {
  def toLabel(record: Record): Label = {
    val keyword: Keyword = Keyword.of(record.get(KEYWORD)).get

    Label(id = LabelId.fromKeyword(keyword),
      displayName = DisplayName(record.get(DISPLAY_NAME)),
      keyword = keyword,
      color = Option.apply(record.get(COLOR))
        .map(Color(_)))
  }
}
