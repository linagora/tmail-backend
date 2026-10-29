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

import java.util.Locale

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LanguageLocationTest {
  @Test
  def fromStringShouldReturnTheLocaleForAValidLanguage(): Unit = {
    assertThat(LanguageLocation.fromString("fr").get.language).isEqualTo(Locale.FRENCH)
  }

  @Test
  def fromStringShouldReturnAFailureForAnUnknownLanguage(): Unit = {
    assertThat(LanguageLocation.fromString("f6").isFailure).isTrue
  }
}
