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

import com.linagora.tmail.james.jmap.label.LabelRepositoryContract.{ALICE, BOB, RED}
import com.linagora.tmail.james.jmap.label.LabelUsernameChangeTaskStepTest.{LABEL_1, LABEL_2}
import com.linagora.tmail.james.jmap.model.{DisplayName, Label, LabelCreationRequest}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object LabelUsernameChangeTaskStepTest {
  val LABEL_1: Label = LabelCreationRequest(DisplayName("Label 1"), Some(RED), None).toLabel
  val LABEL_2: Label = LabelCreationRequest(DisplayName("Label 2"), Some(RED), None).toLabel
}

class LabelUsernameChangeTaskStepTest {
  var labelRepository: LabelRepository = _
  var testee: LabelUsernameChangeTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    labelRepository = new MemoryLabelRepository
    testee = new LabelUsernameChangeTaskStep(labelRepository)
  }

  @Test
  def shouldMigrateLabels(): Unit = {
    SMono(labelRepository.addLabel(ALICE, LABEL_1)).block()
    SMono(labelRepository.addLabel(ALICE, LABEL_2)).block()

    SMono(testee.changeUsername(oldUsername = ALICE, newUsername = BOB)).block()

    assertThat(SFlux(labelRepository.listLabels(BOB)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(LABEL_1, LABEL_2)
  }

  @Test
  def shouldRemoveLabelsFromOldAccount(): Unit = {
    SMono(labelRepository.addLabel(ALICE, LABEL_1)).block()
    SMono(labelRepository.addLabel(ALICE, LABEL_2)).block()

    SMono(testee.changeUsername(oldUsername = ALICE, newUsername = BOB)).block()

    assertThat(SFlux(labelRepository.listLabels(ALICE)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def shouldNotOverrideExistingLabelsOfNewAccount(): Unit = {
    SMono(labelRepository.addLabel(ALICE, LABEL_1)).block()
    SMono(labelRepository.addLabel(BOB, LABEL_2)).block()

    SMono(testee.changeUsername(oldUsername = ALICE, newUsername = BOB)).block()

    assertThat(SFlux(labelRepository.listLabels(BOB)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(LABEL_1, LABEL_2)
  }
}
