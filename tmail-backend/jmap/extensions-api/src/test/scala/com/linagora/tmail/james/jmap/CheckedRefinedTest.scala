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

package com.linagora.tmail.james.jmap

import com.linagora.tmail.james.jmap.CheckedRefined.given
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.types.string.NonEmptyString
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

class CheckedRefinedTest {
  @Test
  def conversionShouldKeepValidString(): Unit = {
    val refined: NonEmptyString = "value"

    assertThat(refined.value).isEqualTo("value")
  }

  @Test
  def conversionShouldRejectInvalidString(): Unit = {
    val empty = ""

    assertThatThrownBy(() => {
      val refined: NonEmptyString = empty
      refined
    }).isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def conversionShouldRejectInvalidNumber(): Unit = {
    val zero = 0

    assertThatThrownBy(() => {
      val refined: Int Refined Positive = zero
      refined
    }).isInstanceOf(classOf[IllegalArgumentException])
  }
}
