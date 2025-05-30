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

package com.linagora.tmail.james.jmap.publicAsset

import java.net.URI
import java.util.UUID

import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.AccountId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PublicAssetURITest {

  @Test
  def fromShouldReturnCorrectPublicURI(): Unit = {
    val prefixUri = new URI("http://localhost:8080")
    val assetId = PublicAssetId(UUID.fromString("a0f7b4b3-0b1b-4b3b-8b3b-0b1b4b3b8b3b"))
    val username = Username.of("username@localhost")
    val result: PublicURI = PublicURI.from(assetId, username, prefixUri)

    assertThat(result.value.toString).isEqualTo("http://localhost:8080/publicAsset/username@localhost/a0f7b4b3-0b1b-4b3b-8b3b-0b1b4b3b8b3b")
  }

  @Test
  def fromShouldReturnCorrectPublicURIWhenPrefixUriContainSeveralPathSegments(): Unit = {
    val prefixUri = new URI("http://localhost:8080/jmap/abc")
    val assetId = PublicAssetId(UUID.fromString("a0f7b4b3-0b1b-4b3b-8b3b-0b1b4b3b8b3b"))
    val account = AccountId("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8")
    val username = Username.of("username@localhost")
    val result: PublicURI = PublicURI.from(assetId, username, prefixUri)

    assertThat(result.value.toString).isEqualTo("http://localhost:8080/jmap/abc/publicAsset/username@localhost/a0f7b4b3-0b1b-4b3b-8b3b-0b1b4b3b8b3b")
  }
}
