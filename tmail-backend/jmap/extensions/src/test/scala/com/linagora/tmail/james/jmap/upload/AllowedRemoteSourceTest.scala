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
 *******************************************************************/

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

package com.linagora.tmail.james.jmap.upload

import java.net.URI

import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

class AllowedRemoteSourceTest {
  @Test
  def shouldMatchExactSourceRegardlessOfPath(): Unit = {
    val source = parse("https://drive.example.com")

    assertThat(source.matches(URI.create("https://drive.example.com/files/downloads/token/file.pdf"))).isTrue
  }

  @Test
  def shouldMatchUsernameWildcardInsideOneLabel(): Unit = {
    val source = parse("https://%-drive.twake.linagora.com")

    assertThat(source.matches(URI.create("https://hqtran-drive.twake.linagora.com/files/downloads/token/file.pdf"))).isTrue
  }

  @Test
  def shouldNotMatchEmptyWildcard(): Unit = {
    val source = parse("https://%-drive.twake.linagora.com")

    assertThat(source.matches(URI.create("https://-drive.twake.linagora.com/file"))).isFalse
  }

  @Test
  def shouldNotMatchAcrossDnsLabels(): Unit = {
    val source = parse("https://%-drive.twake.linagora.com")

    assertThat(source.matches(URI.create("https://alice.extra-drive.twake.linagora.com/file"))).isFalse
  }

  @Test
  def shouldNotMatchExtendedSuffix(): Unit = {
    val source = parse("https://%-drive.twake.linagora.com")

    assertThat(source.matches(URI.create("https://alice-drive.twake.linagora.com.attacker.test/file"))).isFalse
  }

  @Test
  def shouldMatchDefaultAndExplicitHttpsPort(): Unit = {
    val source = parse("https://drive.example.com:443")

    assertThat(source.matches(URI.create("https://drive.example.com/file"))).isTrue
  }

  @Test
  def shouldRejectPathInConfiguredSource(): Unit =
    assertThatThrownBy(() => parse("https://drive.example.com/files"))
      .isInstanceOf(classOf[IllegalArgumentException])

  @Test
  def shouldRejectPercentEncodingInConfiguredHost(): Unit =
    assertThatThrownBy(() => parse("https://%25-drive.twake.linagora.com"))
      .isInstanceOf(classOf[IllegalArgumentException])

  @Test
  def shouldExposeConfiguredValueForCapabilityAdvertisement(): Unit =
    assertThat(parse("  https://%-drive.twake.linagora.com:8443  ").asString)
      .isEqualTo("https://%-drive.twake.linagora.com:8443")

  private def parse(value: String): AllowedRemoteSource =
    AllowedRemoteSource.parse(value).fold(throw _, identity)
}
