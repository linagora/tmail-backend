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

import com.linagora.tmail.james.jmap.blob.UnauthenticatedBlobDownloadTokenRepositoryContract.{ACCOUNT_ID, ACCOUNT_ID_2, BLOB_ID, BLOB_ID_2, RANDOM_TOKEN}
import org.apache.james.blob.api.PlainBlobId
import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

object UnauthenticatedBlobDownloadTokenRepositoryContract {
  val ACCOUNT_ID: AccountId = AccountId.fromString("accountId")
  val ACCOUNT_ID_2: AccountId = AccountId.fromString("accountId2")
  val BLOB_ID: PlainBlobId = new PlainBlobId.Factory().parse("blobId")
  val BLOB_ID_2: PlainBlobId = new PlainBlobId.Factory().parse("blobId2")
  val RANDOM_TOKEN: UnauthenticatedBlobDownloadToken = new UnauthenticatedBlobDownloadToken(UUID.fromString("54b372bb-106c-46e3-a5f3-307eaf37b363"))
}

trait UnauthenticatedBlobDownloadTokenRepositoryContract {
  def testee: UnauthenticatedBlobDownloadTokenRepository
  def advanceClockAfterTtl(): Unit

  @Test
  def unknownTokenShouldReturnFalse(): Unit =
    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, RANDOM_TOKEN).block())
      .isFalse

  @Test
  def generatedTokenShouldValidate(): Unit = {
    val token = testee.generate(ACCOUNT_ID, BLOB_ID).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, token).block())
      .isTrue
  }

  @Test
  def wrongTokenShouldReturnFalse(): Unit = {
    testee.generate(ACCOUNT_ID, BLOB_ID).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, RANDOM_TOKEN).block())
      .isFalse
  }

  @Test
  def correctTokenWithWrongAccountShouldReturnFalse(): Unit = {
    val token = testee.generate(ACCOUNT_ID, BLOB_ID).block()

    assertThat(testee.check(ACCOUNT_ID_2, BLOB_ID, token).block())
      .isFalse
  }

  @Test
  def correctTokenWithWrongBlobShouldReturnFalse(): Unit = {
    val token = testee.generate(ACCOUNT_ID, BLOB_ID).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID_2, token).block())
      .isFalse
  }

  @Test
  def generatingANewTokenShouldInvalidatePreviousTokenForSameBlob(): Unit = {
    val firstToken = testee.generate(ACCOUNT_ID, BLOB_ID).block()
    val secondToken = testee.generate(ACCOUNT_ID, BLOB_ID).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, firstToken).block())
      .isFalse
    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, secondToken).block())
      .isTrue
  }

  @Test
  def tokensForDifferentBlobsShouldBeIndependent(): Unit = {
    val firstToken = testee.generate(ACCOUNT_ID, BLOB_ID).block()
    val secondToken = testee.generate(ACCOUNT_ID, BLOB_ID_2).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, firstToken).block())
      .isTrue
    assertThat(testee.check(ACCOUNT_ID, BLOB_ID_2, secondToken).block())
      .isTrue
  }

  @Test
  def tokensForDifferentAccountsShouldBeIndependent(): Unit = {
    val firstToken = testee.generate(ACCOUNT_ID, BLOB_ID).block()
    val secondToken = testee.generate(ACCOUNT_ID_2, BLOB_ID).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, firstToken).block())
      .isTrue
    assertThat(testee.check(ACCOUNT_ID_2, BLOB_ID, secondToken).block())
      .isTrue
  }

  @Test
  def tokenShouldBeReusableDuringTtl(): Unit = {
    val token = testee.generate(ACCOUNT_ID, BLOB_ID).block()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, token).block())
      .isTrue
    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, token).block())
      .isTrue
  }

  @Test
  def tokenShouldRejectAfterTtl(): Unit = {
    val token = testee.generate(ACCOUNT_ID, BLOB_ID).block()

    advanceClockAfterTtl()

    assertThat(testee.check(ACCOUNT_ID, BLOB_ID, token).block())
      .isFalse
  }
}
