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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LabelValidationTest {
  @Test
  def displayNameShouldAcceptTheMaximumLength(): Unit = {
    val value = "x".repeat(DisplayName.MAX_LENGTH)

    assertThat(DisplayName.validate(value).toOption.get).isEqualTo(DisplayName(value))
  }

  @Test
  def displayNameShouldRejectTooLongValue(): Unit =
    assertThat(DisplayName.validate("x".repeat(DisplayName.MAX_LENGTH + 1)).left.toOption.get.getMessage)
      .isEqualTo("'displayName' must not be longer than 255 characters")

  @ParameterizedTest
  @ValueSource(strings = Array("", " ", "\t"))
  def displayNameShouldRejectBlankValue(value: String): Unit =
    assertThat(DisplayName.validate(value).left.toOption.get.getMessage)
      .isEqualTo("'displayName' is required and must not be empty")

  @Test
  def displayNameShouldRejectNull(): Unit =
    assertThat(DisplayName.validate(null).isLeft).isTrue

  @Test
  def descriptionShouldAcceptTheMaximumLength(): Unit =
    assertThat(LabelDescription.validate("x".repeat(LabelDescription.MAX_LENGTH)).isRight).isTrue

  @Test
  def descriptionShouldAcceptEmptyValue(): Unit =
    assertThat(LabelDescription.validate("").isRight).isTrue

  @Test
  def descriptionShouldRejectTooLongValue(): Unit =
    assertThat(LabelDescription.validate("x".repeat(LabelDescription.MAX_LENGTH + 1)).left.toOption.get.getMessage)
      .isEqualTo("'description' must not be longer than 4096 characters")
}
