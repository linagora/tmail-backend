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

import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

class RemoteUrlPolicyTest {
  @Test
  def shouldApproveMatchingConfiguredSourceWithoutSsrfMode(): Unit = {
    val policy = new RemoteUrlPolicy(UploadFromUrlConfiguration(enabled = true,
      allowedSources = Seq(parse("https://%-drive.twake.linagora.com"))))

    val result = policy.validate("https://hqtran-drive.twake.linagora.com/files/downloads/token/file.pdf")

    assertThat(result.requiresSsrfValidation).isFalse
  }

  @Test
  def shouldApproveMatchingConfiguredSourceOnNonStandardHttpsPort(): Unit = {
    val policy = new RemoteUrlPolicy(UploadFromUrlConfiguration(enabled = true,
      allowedSources = Seq(parse("https://drive.example.com:8443"))))

    val result = policy.validate("https://drive.example.com:8443/files/downloads/token/file.pdf")

    assertThat(result.requiresSsrfValidation).isFalse
  }

  @Test
  def shouldRejectUnmatchedConfiguredSource(): Unit = {
    val policy = new RemoteUrlPolicy(UploadFromUrlConfiguration(enabled = true,
      allowedSources = Seq(parse("https://drive.example.com"))))

    assertThatThrownBy(() => policy.validate("https://attacker.example/file"))
      .isInstanceOf(classOf[RemoteUrlRejectedException])
  }

  @Test
  def shouldSelectSsrfModeWithoutSources(): Unit = {
    val policy = new RemoteUrlPolicy(UploadFromUrlConfiguration(enabled = true, allowedSources = Seq.empty))

    assertThat(policy.validate("https://drive.example.com/file").requiresSsrfValidation).isTrue
  }

  @Test
  def shouldRejectNonStandardPortInSsrfMode(): Unit = {
    val policy = new RemoteUrlPolicy(UploadFromUrlConfiguration(enabled = true, allowedSources = Seq.empty))

    assertThatThrownBy(() => policy.validate("https://drive.example.com:8443/file"))
      .isInstanceOf(classOf[RemoteUrlRejectedException])
  }

  @Test
  def shouldRejectCredentials(): Unit = {
    val policy = new RemoteUrlPolicy(UploadFromUrlConfiguration(enabled = true, allowedSources = Seq.empty))

    assertThatThrownBy(() => policy.validate("https://user:password@drive.example.com/file"))
      .isInstanceOf(classOf[RemoteUrlRejectedException])
  }

  @Test
  def shouldRejectFragment(): Unit = {
    val policy = new RemoteUrlPolicy(UploadFromUrlConfiguration(enabled = true, allowedSources = Seq.empty))

    assertThatThrownBy(() => policy.validate("https://drive.example.com/file#fragment"))
      .isInstanceOf(classOf[RemoteUrlRejectedException])
  }

  private def parse(value: String): AllowedRemoteSource =
    AllowedRemoteSource.parse(value).fold(throw _, identity)
}
