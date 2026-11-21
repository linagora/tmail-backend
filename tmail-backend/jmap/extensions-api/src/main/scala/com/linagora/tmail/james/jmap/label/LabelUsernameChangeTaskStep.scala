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

import com.linagora.tmail.james.jmap.model.Label
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.mail.Keyword
import org.apache.james.user.api.UsernameChangeTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

class LabelUsernameChangeTaskStep @Inject() (labelRepository: LabelRepository) extends UsernameChangeTaskStep {
  override def name(): UsernameChangeTaskStep.StepName = new UsernameChangeTaskStep.StepName("LabelUsernameChangeTaskStep")

  override def priority(): Int = 8

  // On a keyword collision the label of the new user is kept, as RateLimitingUsernameChangeTaskStep keeps its limits
  override def changeUsername(oldUsername: Username, newUsername: Username): Publisher[Void] =
    SFlux(labelRepository.listLabels(newUsername))
      .map(_.keyword)
      .collectSeq()
      .map(_.toSet)
      .flatMapMany(newUserKeywords => SFlux(labelRepository.listLabels(oldUsername))
        .flatMap(label => migrateIfAbsent(newUsername, label, newUserKeywords)
          .`then`(SMono(labelRepository.deleteLabel(oldUsername, label.id)))))

  private def migrateIfAbsent(newUsername: Username, label: Label, newUserKeywords: Set[Keyword]): SMono[Unit] =
    if (newUserKeywords.contains(label.keyword)) {
      SMono.empty
    } else {
      SMono(labelRepository.addLabel(newUsername, label)).`then`()
    }
}
