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

package com.linagora.tmail.james.jmap.label

import com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelTable.{COLOR, DISPLAY_NAME, DOCUMENTATION, KEYWORD}
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
        .map(Color(_)),
      documentation = Option.apply(record.get(DOCUMENTATION)))
  }
}
