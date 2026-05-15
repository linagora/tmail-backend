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

import java.time.{Duration, Instant}

import com.linagora.tmail.james.jmap.blob.UnauthenticatedBlobDownloadTokenRepositoryContract.{ACCOUNT_ID, BLOB_ID}
import org.apache.james.utils.UpdatableTickingClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

class MemoryUnauthenticatedBlobDownloadTokenRepositoryTest extends UnauthenticatedBlobDownloadTokenRepositoryContract {
  val initialInstant: Instant = Instant.parse("2026-05-13T00:00:00Z")
  val tokenTtl: Duration = Duration.ofMinutes(5)
  var clock: UpdatableTickingClock = _
  var repository: MemoryUnauthenticatedBlobDownloadTokenRepository = _

  @BeforeEach
  def setUp(): Unit = {
    clock = new UpdatableTickingClock(initialInstant)
    repository = new MemoryUnauthenticatedBlobDownloadTokenRepository(clock, tokenTtl)
  }

  override def testee: UnauthenticatedBlobDownloadTokenRepository = repository
  override def advanceClockAfterTtl(): Unit = clock.setInstant(initialInstant.plus(tokenTtl))

  @Test
  def expiredTokenShouldBeRemovedLazily(): Unit = {
    val token = repository.generate(ACCOUNT_ID, BLOB_ID).block()

    advanceClockAfterTtl()
    repository.check(ACCOUNT_ID, BLOB_ID, token).block()

    assertThat(repository.isStored(ACCOUNT_ID, BLOB_ID))
      .isFalse
  }
}
