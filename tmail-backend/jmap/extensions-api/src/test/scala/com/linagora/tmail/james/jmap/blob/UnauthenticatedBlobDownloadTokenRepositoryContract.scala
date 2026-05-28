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

package com.linagora.tmail.james.jmap.blob

import java.util.UUID

import com.linagora.tmail.james.jmap.blob.UnauthenticatedBlobDownloadTokenRepositoryContract.{ACCOUNT_ID, ACCOUNT_ID_2, BLOB_ID, BLOB_ID_2, RANDOM_TOKEN, USERNAME, USERNAME_2}
import org.apache.james.blob.api.PlainBlobId
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

object UnauthenticatedBlobDownloadTokenRepositoryContract {
  val ACCOUNT_ID: AccountId = AccountId.fromString("accountId")
  val ACCOUNT_ID_2: AccountId = AccountId.fromString("accountId2")
  val BLOB_ID: PlainBlobId = new PlainBlobId.Factory().parse("blobId")
  val BLOB_ID_2: PlainBlobId = new PlainBlobId.Factory().parse("blobId2")
  val USERNAME: Username = Username.of("bob@domain.tld")
  val USERNAME_2: Username = Username.of("alice@domain.tld")
  val RANDOM_TOKEN: UnauthenticatedBlobDownloadToken = new UnauthenticatedBlobDownloadToken(UUID.fromString("54b372bb-106c-46e3-a5f3-307eaf37b363"))
}

trait UnauthenticatedBlobDownloadTokenRepositoryContract {
  def testee: UnauthenticatedBlobDownloadTokenRepository
  def advanceClockAfterTtl(): Unit

  @Test
  def unknownTokenShouldReturnEmpty(): Unit =
    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, RANDOM_TOKEN).block())
      .isEmpty

  @Test
  def generatedTokenShouldReturnUsername(): Unit = {
    val token = testee.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, token).block())
      .contains(USERNAME)
  }

  @Test
  def wrongTokenShouldReturnEmpty(): Unit = {
    testee.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, RANDOM_TOKEN).block())
      .isEmpty
  }

  @Test
  def correctTokenWithWrongAccountShouldReturnEmpty(): Unit = {
    val token = testee.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block()

    assertThat(testee.check(ACCOUNT_ID_2, BLOB_ID, token).block())
      .isEmpty
  }

  @Test
  def correctTokenWithWrongBlobShouldReturnEmpty(): Unit = {
    val token = testee.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID_2, token).block())
      .isEmpty
  }

  @Test
  def generatingANewTokenShouldInvalidatePreviousTokenForSameBlob(): Unit = {
    val firstToken = testee.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block()
    val secondToken = testee.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, firstToken).block())
      .isEmpty
    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, secondToken).block())
      .contains(USERNAME)
  }

  @Test
  def tokensForDifferentBlobsShouldBeIndependent(): Unit = {
    val firstToken = testee.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block()
    val secondToken = testee.generate(ACCOUNT_ID, BLOB_ID_2, USERNAME).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, firstToken).block())
      .contains(USERNAME)
    assertThat(testee.check(ACCOUNT_ID, BLOB_ID_2, secondToken).block())
      .contains(USERNAME)
  }

  @Test
  def tokensForDifferentAccountsShouldBeIndependent(): Unit = {
    val firstToken = testee.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block()
    val secondToken = testee.generate(ACCOUNT_ID_2, BLOB_ID, USERNAME_2).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, firstToken).block())
      .contains(USERNAME)
    assertThat(testee.check(ACCOUNT_ID_2, BLOB_ID, secondToken).block())
      .contains(USERNAME_2)
  }

  @Test
  def tokenShouldBeReusableDuringTtl(): Unit = {
    val token = testee.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, token).block())
      .contains(USERNAME)
    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, token).block())
      .contains(USERNAME)
  }

  @Test
  def tokenShouldRejectAfterTtl(): Unit = {
    val token = testee.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block()

    advanceClockAfterTtl()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, token).block())
      .isEmpty
  }
}
