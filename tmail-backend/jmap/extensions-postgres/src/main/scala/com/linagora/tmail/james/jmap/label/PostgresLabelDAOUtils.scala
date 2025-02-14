/** ******************************************************************
 * As a subpart of Twake Mail, this file is edited by Linagora.    *
 * *
 * https://twake-mail.com/                                         *
 * https://linagora.com                                            *
 * *
 * This file is subject to The Affero Gnu Public License           *
 * version 3.                                                      *
 * *
 * https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 * *
 * This program is distributed in the hope that it will be         *
 * useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 * PURPOSE. See the GNU Affero General Public License for          *
 * more details.                                                   *
 * *
 * This file was taken and adapted from the Apache James project.  *
 * *
 * https://james.apache.org                                        *
 * *
 * It was originally licensed under the Apache V2 license.         *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                      *
 * ****************************************************************** */

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
