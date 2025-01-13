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

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.UsernameChangeTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class JmapSettingsUsernameChangeTaskStep @Inject()(repository: JmapSettingsRepository) extends UsernameChangeTaskStep {
  override def name(): UsernameChangeTaskStep.StepName = new UsernameChangeTaskStep.StepName("JmapSettingsUsernameChangeTaskStep")

  override def priority(): Int = 9

  override def changeUsername(oldUsername: Username, newUsername: Username): Publisher[Void] =
    SMono(repository.get(oldUsername))
      .flatMap(oldAccountSettings => SMono(repository.get(newUsername))
        .flatMap(newAccountSettings => mergeSettingsAndFullReset(newUsername, oldAccountSettings, newAccountSettings))
        .switchIfEmpty(overrideWithOldAccountSettings(newUsername, oldAccountSettings))
        .`then`(SMono(repository.delete(oldUsername))))

  private def mergeSettingsAndFullReset(newUsername: Username, oldAccountSettings: JmapSettings, newAccountSettings: JmapSettings): SMono[SettingsStateUpdate] =
    SMono(repository.reset(newUsername, JmapSettingsUpsertRequest(oldAccountSettings.settings ++ newAccountSettings.settings)))

  private def overrideWithOldAccountSettings(newUsername: Username, oldAccountSettings: JmapSettings): SMono[SettingsStateUpdate] =
    SMono(repository.reset(newUsername, JmapSettingsUpsertRequest(oldAccountSettings.settings)))
}
