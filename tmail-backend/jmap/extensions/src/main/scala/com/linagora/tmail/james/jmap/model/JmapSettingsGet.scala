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

package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.model.JmapSettingsObject.{JmapSettingsId, SETTING_SINGLETON_ID}
import com.linagora.tmail.james.jmap.settings.{JmapSettings, JmapSettingsKey, JmapSettingsStateFactory, JmapSettingsValue}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Id, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class JmapSettingsGet(accountId: AccountId,
                           ids: Option[Set[JmapSettingsId]] = None) extends WithAccountId
case class JmapSettingsResponse(accountId: AccountId,
                                state: UuidState,
                                list: Seq[JmapSettingsObject],
                                notFound: Seq[JmapSettingsId])

object JmapSettingsObject {

  type JmapSettingsId = Id

  val SETTING_SINGLETON_ID: JmapSettingsId = Id.validate("singleton").toOption.get

  def singleton(jmapSettings: JmapSettings): JmapSettingsObject =
    JmapSettingsObject(SETTING_SINGLETON_ID, jmapSettings.state, jmapSettings.settings)

  def unapplyIgnoreState(settingsEntry: JmapSettingsObject): Some[(JmapSettingsId, Map[JmapSettingsKey, JmapSettingsValue])] =
    Some((settingsEntry.id, settingsEntry.settings))
}

case class JmapSettingsObject(id: JmapSettingsId,
                              state: UuidState = JmapSettingsStateFactory.INITIAL,
                              settings: Map[JmapSettingsKey, JmapSettingsValue])

object JmapSettingsGetResult {

  def empty: JmapSettingsGetResult = JmapSettingsGetResult(Set(), Set())

  def notFound(id: JmapSettingsId): JmapSettingsGetResult = JmapSettingsGetResult(Set(), Set(id))

  def merge(r1: JmapSettingsGetResult, r2: JmapSettingsGetResult): JmapSettingsGetResult =
    JmapSettingsGetResult(r1.settingsEntries ++ r2.settingsEntries, r1.notFound ++ r2.notFound)

  def emptySingleton(): JmapSettingsGetResult =
    JmapSettingsGetResult(Set(JmapSettingsObject(SETTING_SINGLETON_ID, JmapSettingsStateFactory.INITIAL, Map())), Set())

  def singleton(jmapSettings: JmapSettings): JmapSettingsGetResult =
    JmapSettingsGetResult(Set(JmapSettingsObject.singleton(jmapSettings)), Set())
}

case class JmapSettingsGetResult(settingsEntries: Set[JmapSettingsObject],
                                 notFound: Set[JmapSettingsId] = Set()) {
  def asResponse(accountId: AccountId): JmapSettingsResponse =
    JmapSettingsResponse(
      accountId = accountId,
      state = settingsEntries.headOption.map(_.state).getOrElse(JmapSettingsStateFactory.INITIAL),
      list = settingsEntries.toSeq,
      notFound = notFound.toSeq)
}