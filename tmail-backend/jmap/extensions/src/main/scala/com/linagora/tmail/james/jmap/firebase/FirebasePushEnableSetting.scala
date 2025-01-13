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

package com.linagora.tmail.james.jmap.firebase

import java.util.Locale

import com.linagora.tmail.james.jmap.settings.{JmapSettingEntry, JmapSettingParser, JmapSettingsKey, JmapSettingsValue}

case class FirebasePushEnableSetting(enabled: Boolean) extends JmapSettingEntry

case object FirebasePushEnableSettingParser extends JmapSettingParser[FirebasePushEnableSetting] {
  val ENABLED: FirebasePushEnableSetting = FirebasePushEnableSetting(defaultValue().value.toBoolean)

  override def key(): JmapSettingsKey = JmapSettingsKey.liftOrThrow("firebase.enabled")

  override def defaultValue(): JmapSettingsValue = JmapSettingsValue("true")

  override def parse(value: Option[JmapSettingsValue]): FirebasePushEnableSetting =
    value.map(_.value.toLowerCase(Locale.US)) match {
      case Some("false") => FirebasePushEnableSetting(false)
      case _ => ENABLED
    }
}
