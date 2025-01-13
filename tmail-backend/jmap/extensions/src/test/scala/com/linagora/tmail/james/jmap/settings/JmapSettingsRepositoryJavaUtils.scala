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
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

case class JmapSettingsRepositoryJavaUtils(jmapSettingsRepository: JmapSettingsRepository) {
  def reset(username: Username, settingsJava: java.util.Map[String, String]): SettingsStateUpdate = {
    val settingsScala: Map[String, String] = settingsJava.asScala.toMap
    val upsertRequest: JmapSettingsUpsertRequest = JmapSettingsUpsertRequest(settingsScala.map(kv => JmapSettingsKey.liftOrThrow(kv._1) -> JmapSettingsValue(kv._2)))
    SMono(jmapSettingsRepository.reset(username, upsertRequest)).block()
  }
}
