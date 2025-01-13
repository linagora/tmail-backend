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

import java.time.ZonedDateTime

import com.linagora.tmail.james.jmap.label.LabelChangeRepositoryContract.DATE
import org.apache.james.jmap.api.change.State
import org.apache.james.utils.UpdatableTickingClock
import org.junit.jupiter.api.BeforeEach

class MemoryLabelChangeRepositoryTest extends LabelChangeRepositoryContract {
  var repository: MemoryLabelChangeRepository = _
  var updatableTickingClock: UpdatableTickingClock = _

  override def testee: LabelChangeRepository = repository

  override def stateFactory: State.Factory = State.Factory.DEFAULT

  override def setClock(newTime: ZonedDateTime): Unit = updatableTickingClock.setInstant(newTime.toInstant)

  @BeforeEach
  def setup(): Unit = {
    updatableTickingClock = new UpdatableTickingClock(DATE.toInstant)
    repository = MemoryLabelChangeRepository(updatableTickingClock)
  }
}