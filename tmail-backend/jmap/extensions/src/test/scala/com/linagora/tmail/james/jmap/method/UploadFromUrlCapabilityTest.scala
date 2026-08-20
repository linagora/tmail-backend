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

package com.linagora.tmail.james.jmap.method

import java.net.URI

import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_UPLOAD_FROM_URL
import com.linagora.tmail.james.jmap.upload.{AllowedRemoteSource, UploadFromUrlConfiguration}
import org.apache.james.core.Username
import org.apache.james.jmap.core.UrlPrefixes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UploadFromUrlCapabilityTest {
  @Test
  def shouldAdvertiseUploadUrlAndAllowedSources(): Unit = {
    val configuration = UploadFromUrlConfiguration(
      enabled = true,
      allowedSources = Seq(
        parseSource("https://drive.example.com"),
        parseSource("https://%-drive.twake.example.com")))
    val capability = new UploadFromUrlCapabilityFactory(configuration)
      .create(UrlPrefixes(new URI("https://jmap.example.com"), new URI("wss://jmap.example.com")), Username.of("bob@example.com"))

    assertThat(capability.identifier()).isEqualTo(LINAGORA_UPLOAD_FROM_URL)
    assertThat(capability.properties().jsonify().toString())
      .isEqualTo("{\"uploadUrl\":\"https://jmap.example.com/upload-from-url/{accountId}\",\"allowedSources\":[\"https://drive.example.com\",\"https://%-drive.twake.example.com\"]}")
  }

  @Test
  def shouldOmitAllowedSourcesWhenUsingDynamicSsrfPolicy(): Unit = {
    val capability = new UploadFromUrlCapabilityFactory(UploadFromUrlConfiguration.DISABLED.copy(enabled = true))
      .create(UrlPrefixes(new URI("https://jmap.example.com"), new URI("wss://jmap.example.com")), Username.of("bob@example.com"))

    assertThat(capability.properties().jsonify().toString())
      .isEqualTo("{\"uploadUrl\":\"https://jmap.example.com/upload-from-url/{accountId}\"}")
  }

  private def parseSource(value: String): AllowedRemoteSource =
    AllowedRemoteSource.parse(value).fold(throw _, identity)
}
