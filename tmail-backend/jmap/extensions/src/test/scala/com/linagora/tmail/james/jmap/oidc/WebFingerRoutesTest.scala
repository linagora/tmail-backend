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

package com.linagora.tmail.james.jmap.oidc

import java.net.URI

import com.linagora.tmail.james.jmap.WebFingerConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class WebFingerRoutesTest {
  private val REL = "http://openid.net/specs/connect/1.0/issuer"
  private val testee = new WebFingerRoutes(WebFingerConfiguration(Some(new URI("https://auth.example.com/realms/jmap").toURL)))

  @ParameterizedTest
  @ValueSource(strings = Array("%3A", ":", "relative/path", "/absolute/path", "%"))
  def asWebFingerRequestShouldRejectInvalidResourcesAsBadRequest(resource: String): Unit =
    assertThat(testee.asWebFingerRequest(Some(resource), Some(REL)).failed.get)
      .isInstanceOf(classOf[IllegalArgumentException])

  @ParameterizedTest
  @ValueSource(strings = Array("https://jmap.example.com", "acct:bob@example.com", "mailto:bob@example.com"))
  def asWebFingerRequestShouldAcceptAbsoluteUris(resource: String): Unit =
    assertThat(testee.asWebFingerRequest(Some(resource), Some(REL)).get)
      .isEqualTo(WebFingerRequest(new URI(resource)))

  @ParameterizedTest
  @ValueSource(strings = Array("acct%3Abob%40example.com"))
  def asWebFingerRequestShouldDecodeResource(resource: String): Unit =
    assertThat(testee.asWebFingerRequest(Some(resource), Some(REL)).get)
      .isEqualTo(WebFingerRequest(new URI("acct:bob@example.com")))
}
