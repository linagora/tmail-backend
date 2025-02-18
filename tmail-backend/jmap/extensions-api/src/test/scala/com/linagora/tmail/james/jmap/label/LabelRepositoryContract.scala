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

import com.linagora.tmail.james.jmap.label.LabelRepositoryContract.{ALICE, BLUE, BOB, RED}
import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelCreationRequest, LabelId, LabelNotFoundException}
import org.apache.james.core.Username
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object LabelRepositoryContract {
  val ALICE: Username = Username.of("alice@domain.com")
  val BOB: Username = Username.of("bob@domain.com")
  val RED: Color = Color("#FF0000")
  val BLUE: Color = Color("#0000FF")
}

trait LabelRepositoryContract {
  def testee: LabelRepository

  @Test
  def addLabelFromCreationRequestShouldSucceed(): Unit = {
    val creationRequest: LabelCreationRequest = LabelCreationRequest(DisplayName("Important"), Some(RED))
    val createdLabel: Label = SMono.fromPublisher(testee.addLabel(ALICE, creationRequest)).block()

    assertThat(createdLabel.displayName).isEqualTo(DisplayName("Important"))
    assertThat(createdLabel.color).isEqualTo(Some(RED))
  }

  @Test
  def addLabelShouldSucceed(): Unit = {
    val label: Label = LabelCreationRequest(DisplayName("Important"), Some(RED)).toLabel
    SMono.fromPublisher(testee.addLabel(ALICE, label)).block()

    assertThat(SMono.fromPublisher(testee.listLabels(ALICE)).block())
      .isEqualTo(label)
  }

  @Test
  def addLabelFromCreationRequestShouldGenerateRandomValuesForLabelIdAndKeyword(): Unit = {
    val creationRequest1: LabelCreationRequest = LabelCreationRequest(DisplayName("Important"), Some(RED))
    val creationRequest2: LabelCreationRequest = LabelCreationRequest(DisplayName("Later"), Some(RED))

    val createdLabel1: Label = SMono.fromPublisher(testee.addLabel(ALICE, creationRequest1)).block()
    val createdLabel2: Label = SMono.fromPublisher(testee.addLabel(ALICE, creationRequest2)).block()

    assertThat(createdLabel1.id).isNotEqualTo(createdLabel2.id)
    assertThat(createdLabel1.keyword).isNotEqualTo(createdLabel2.keyword)
  }

  @Test
  def addLabelFromCreationRequestShouldSupportCreatingSameLabelDisplayNameForDifferentUsers(): Unit = {
    val creationRequest: LabelCreationRequest = LabelCreationRequest(DisplayName("Important"), Some(RED))
    val aliceCreatedLabel: Label = SMono.fromPublisher(testee.addLabel(ALICE, creationRequest)).block()
    val bobCreatedLabel: Label = SMono.fromPublisher(testee.addLabel(BOB, creationRequest)).block()

    assertThat(aliceCreatedLabel.displayName).isEqualTo(DisplayName("Important"))
    assertThat(bobCreatedLabel.displayName).isEqualTo(DisplayName("Important"))
  }

  @Test
  def addLabelFromCreationRequestShouldSupportCreatingSameLabelDisplayNameForAUser(): Unit = {
    val creationRequest: LabelCreationRequest = LabelCreationRequest(DisplayName("Important"), Some(RED))
    val existingLabel: Label = SMono.fromPublisher(testee.addLabel(ALICE, creationRequest)).block()
    val newLabel: Label = SMono.fromPublisher(testee.addLabel(ALICE, creationRequest)).block()

    val labels = SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block()

    assertThat(labels.asJava).containsExactlyInAnyOrder(existingLabel, newLabel)
    assertThat(existingLabel.keyword).isNotEqualTo(newLabel.keyword)
  }

  @Test
  def addLabelsFromCreationRequestShouldSucceed(): Unit = {
    val creationRequest1: LabelCreationRequest = LabelCreationRequest(DisplayName("Important"), Some(RED))
    val creationRequest2: LabelCreationRequest = LabelCreationRequest(DisplayName("Later"), Some(RED))
    SFlux.fromPublisher(testee.addLabels(ALICE, java.util.List.of(creationRequest1, creationRequest2))).collectSeq().block()

    val labels = SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block()

    assertThat(labels.asJava).hasSize(2)
  }

  @Test
  def updateNonExistingLabelShouldThrowException(): Unit = {
    val randomLabelId = LabelId.generate()

    assertThatThrownBy(() => SMono.fromPublisher(testee.updateLabel(ALICE, randomLabelId, Some(DisplayName("New display name")))).block())
      .isInstanceOf(classOf[LabelNotFoundException])
  }

  @Test
  def updateOnlyDisplayNameShouldSucceed(): Unit = {
    val labelId = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block().id

    SMono.fromPublisher(testee.updateLabel(ALICE, labelId, Some(DisplayName("New display name")))).block()

    assertThat(SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block().head.displayName)
      .isEqualTo(DisplayName("New display name"))
  }

  @Test
  def updateOnlyDisplayNameShouldNotOverrideColor(): Unit = {
    val labelId = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block().id

    SMono.fromPublisher(testee.updateLabel(ALICE, labelId, Some(DisplayName("New display name")))).block()

    assertThat(SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block().head.color)
      .isEqualTo(Some(RED))
  }

  @Test
  def updateOnlyColorShouldSucceed(): Unit = {
    val labelId = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block().id

    SMono.fromPublisher(testee.updateLabel(ALICE, labelId, newColor = Some(BLUE))).block()

    assertThat(SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block().head.color)
      .isEqualTo(Some(BLUE))
  }

  @Test
  def updateOnlyColorShouldNotOverrideDisplayName(): Unit = {
    val labelId = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block().id

    SMono.fromPublisher(testee.updateLabel(ALICE, labelId, newColor = Some(BLUE))).block()

    assertThat(SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block().head.displayName)
      .isEqualTo(DisplayName("Important"))
  }

  @Test
  def updateBothDisplayNameAndColorShouldSucceed(): Unit = {
    val labelId = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block().id

    SMono.fromPublisher(testee.updateLabel(ALICE, labelId,
      newDisplayName = Some(DisplayName("New Display Name")),
      newColor = Some(BLUE))).block()

    val updatedLabel = SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block().head

    assertThat(updatedLabel.displayName).isEqualTo(DisplayName("New Display Name"))
    assertThat(updatedLabel.color).isEqualTo(Some(BLUE))
  }

  @Test
  def updateBothEmptyDisplayNameAndColorShouldNotOverrideAnything(): Unit = {
    val labelId = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block().id

    SMono.fromPublisher(testee.updateLabel(ALICE, labelId, newDisplayName = None, newColor = None)).block()

    assertThat(SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block().head.displayName)
      .isEqualTo(DisplayName("Important"))
    assertThat(SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block().head.color)
      .isEqualTo(Some(RED))
  }

  @Test
  def updateShouldBeIdempotent(): Unit = {
    val labelId = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block().id

    SMono.fromPublisher(testee.updateLabel(ALICE, labelId,
      newDisplayName = Some(DisplayName("New Display Name")))).block()

    assertThatCode(() => SMono.fromPublisher(testee.updateLabel(
      ALICE, labelId, newDisplayName = Some(DisplayName("New Display Name"))))
      .block())
      .doesNotThrowAnyException()

    val updatedLabel = SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block().head
    assertThat(updatedLabel.displayName).isEqualTo(DisplayName("New Display Name"))
  }

  @Test
  def getNonExistingLabelsShouldReturnEmpty(): Unit = {
    val randomLabelId = LabelId.generate()

    val labels = SFlux.fromPublisher(testee.getLabels(ALICE, java.util.List.of(randomLabelId)))
      .collectSeq().block()

    assertThat(labels.asJava).isEmpty()
  }

  @Test
  def getShouldReturnRequestedLabels(): Unit = {
    val label1 = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED)))).block()
    val label2 = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED)))).block()
    val label3 = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED)))).block()

    val labels = SFlux.fromPublisher(testee.getLabels(ALICE, java.util.List.of(label1.id, label2.id)))
      .collectSeq().block()

    assertThat(labels.asJava).containsExactlyInAnyOrder(label1, label2)
  }

  @Test
  def getShouldNotReturnOtherUsersLabels(): Unit = {
    val aliceLabel = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED)))).block()
    val bobLabel = SMono.fromPublisher(testee.addLabel(BOB, LabelCreationRequest(DisplayName("Important"), Some(RED)))).block()

    val labels = SFlux.fromPublisher(testee.getLabels(ALICE, java.util.List.of(aliceLabel.id)))
      .collectSeq().block()

    assertThat(labels.asJava).containsOnly(aliceLabel)
  }

  @Test
  def listShouldReturnEmptyByDefault(): Unit = {
    val labels = SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block()

    assertThat(labels.asJava).isEmpty()
  }

  @Test
  def listShouldReturnAllLabelsOfTheUser(): Unit = {
    val label1 = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED)))).block()
    val label2 = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED)))).block()

    val labels = SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block()

    assertThat(labels.asJava).containsExactlyInAnyOrder(label1, label2)
  }

  @Test
  def listShouldNotReturnOtherUsersLabels(): Unit = {
    val aliceLabel = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED)))).block()
    val bobLabel = SMono.fromPublisher(testee.addLabel(BOB, LabelCreationRequest(DisplayName("Important"), Some(RED)))).block()

    val labels = SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block()

    assertThat(labels.asJava).containsOnly(aliceLabel)
  }

  @Test
  def deleteShouldSucceed(): Unit = {
    val label1 = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block()
    val label2 = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block()

    SMono.fromPublisher(testee.deleteLabel(ALICE, label1.id)).block()

    val labels = SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block()
    assertThat(labels.asJava).containsOnly(label2)
  }

  @Test
  def deleteShouldNotRemoveOtherUsersLabels(): Unit = {
    val aliceLabel = SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block()
    val bobLabel = SMono.fromPublisher(testee.addLabel(BOB, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block()

    SMono.fromPublisher(testee.deleteLabel(ALICE, aliceLabel.id)).block()

    val labels = SFlux.fromPublisher(testee.listLabels(BOB)).collectSeq().block()
    assertThat(labels.asJava).containsOnly(bobLabel)
  }

  @Test
  def deleteShouldBeIdempotent(): Unit = {
    val randomLabelId = LabelId.generate()

    assertThatCode(() => SMono.fromPublisher(testee.deleteLabel(ALICE, randomLabelId)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def deleteAllShouldSucceed(): Unit = {
    SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block()
    SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block()

    SMono.fromPublisher(testee.deleteAllLabels(ALICE)).block()

    assertThat(SFlux.fromPublisher(testee.listLabels(ALICE)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def deleteAllShouldNotDeleteLabelsOfOtherUsers(): Unit = {
    SMono.fromPublisher(testee.addLabel(ALICE, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block()
    val bobLabel = SMono.fromPublisher(testee.addLabel(BOB, LabelCreationRequest(DisplayName("Important"), Some(RED))))
      .block()

    SMono.fromPublisher(testee.deleteAllLabels(ALICE)).block()

    assertThat(SFlux.fromPublisher(testee.listLabels(BOB)).collectSeq().block().asJava)
      .containsOnly(bobLabel)
  }
}
