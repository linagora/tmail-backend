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

package com.linagora.tmail.james.jmap.settings

import org.apache.james.core.Username
import org.apache.james.jmap.core.UuidState
import org.reactivestreams.Publisher

trait JmapSettingsRepository {
  def get(username: Username): Publisher[JmapSettings]

  def getLatestState(username: Username): Publisher[UuidState]

  def reset(username: Username, settings: JmapSettingsUpsertRequest): Publisher[SettingsStateUpdate]

  def updatePartial(username: Username, settingsPatch: JmapSettingsPatch): Publisher[SettingsStateUpdate]

  def delete(username: Username): Publisher[Void]
}
