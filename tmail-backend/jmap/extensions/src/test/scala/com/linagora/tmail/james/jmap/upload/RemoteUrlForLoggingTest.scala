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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RemoteUrlForLoggingTest {
  @Test
  def shouldRemoveCredentialsAndFragment(): Unit = {
    assertThat(RemoteUrlForLogging.sanitize("https://alice:secret@drive.example.com:8443/files/report.pdf?token=secret#section"))
      .isEqualTo("https://drive.example.com:8443/…/report.pdf")
  }

  @Test
  def shouldRedactDrivePathTokenAndKeepFileName(): Unit = {
    // (redacted) existing TDrive short-live URL on staging
    assertThat(RemoteUrlForLogging.sanitize("https://drive.example.com/files/downloads/07837a3175078341/mark_read_mixin_improvements.patch"))
      .isEqualTo("https://drive.example.com/…/mark_read_mixin_improvements.patch")
  }

  @Test
  def shouldPreserveEncodedFileName(): Unit = {
    assertThat(RemoteUrlForLogging.sanitize("https://drive.example.com/files/My%20report.pdf?token=secret"))
      .isEqualTo("https://drive.example.com/…/My%20report.pdf")
  }

  @Test
  def shouldOmitPathWithoutFileName(): Unit = {
    assertThat(RemoteUrlForLogging.sanitize("https://drive.example.com/files/downloads/token/"))
      .isEqualTo("https://drive.example.com")
  }

  @Test
  def shouldNotExposeMalformedValue(): Unit = {
    assertThat(RemoteUrlForLogging.sanitize("not a URL?token=secret"))
      .isEqualTo(RemoteUrlForLogging.INVALID_URL)
  }
}
