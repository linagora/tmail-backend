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

import com.linagora.tmail.james.jmap.label.LabelRepositoryContract.{ALICE, BOB}
import com.linagora.tmail.james.jmap.label.LabelUsernameChangeTaskStepTest.{LABEL_1, LABEL_2}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class LabelUserDeletionTaskStepTest {
  var labelRepository: LabelRepository = _
  var testee: LabelUserDeletionTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    labelRepository = new MemoryLabelRepository
    testee = new LabelUserDeletionTaskStep(labelRepository)
  }

  @Test
  def shouldRemoveLabels(): Unit = {
    SMono(labelRepository.addLabel(ALICE, LABEL_1)).block()
    SMono(labelRepository.addLabel(ALICE, LABEL_2)).block()

    SMono(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux(labelRepository.listLabels(ALICE)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def shouldNotRemoveOtherUsersLabels(): Unit = {
    SMono(labelRepository.addLabel(ALICE, LABEL_1)).block()
    SMono(labelRepository.addLabel(BOB, LABEL_2)).block()

    SMono(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux(labelRepository.listLabels(BOB)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(LABEL_2)
  }

  @Test
  def shouldBeIdempotent(): Unit = {
    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThatCode(() => SMono.fromPublisher(testee.deleteUserData(ALICE)).block())
      .doesNotThrowAnyException()
  }
}
