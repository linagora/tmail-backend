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

package com.linagora.tmail.james.jmap.contact

import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.{BeforeEach, Disabled, Test}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

class MemoryEmailAddressContactSearchEngineTest extends EmailAddressContactSearchEngineContract {
  private val accountId: AccountId = AccountId.fromUsername(Username.of("bob@linagora.com"))

  var inMemoryEmailAddressContactSearchEngine: InMemoryEmailAddressContactSearchEngine = _

  override def testee(): EmailAddressContactSearchEngine = inMemoryEmailAddressContactSearchEngine

  @BeforeEach
  def beforeEach(): Unit = {
    inMemoryEmailAddressContactSearchEngine = new InMemoryEmailAddressContactSearchEngine()
  }

  override def awaitDocumentsIndexed(query: QueryType, documentCount: Long): Unit = {
  }

  @Test
  @Disabled("Memory does not need to support language special characters normalization")
  override def searchASCIICharactersShouldReturnMatchedFrenchName(): Unit = {
  }

  @Test
  def autoCompleteShouldNotThrowConcurrentModificationExceptionWhenAutoCompleteAndIndexingConcurrently(): Unit = {
    val initialContactCount = 10

    SFlux.range(0, initialContactCount)
      .flatMap(index => SMono(testee().index(accountId, contact(index))))
      .blockLast()

    val autoCompleteContactsConcurrently: SFlux[Seq[EmailAddressContact]] = SFlux.range(0, initialContactCount)
      .flatMap(_ => SFlux(testee().autoComplete(accountId, "contact", 10_000))
        .collectSeq()
        .subscribeOn(Schedulers.parallel()))

    val indexContactsConcurrently: SFlux[EmailAddressContact] = SFlux.range(initialContactCount, 10)
      .flatMap(index => SMono(testee().index(accountId, contact(index)))
        .subscribeOn(Schedulers.parallel()))

    assertThatCode(() => SFlux.merge(Seq(autoCompleteContactsConcurrently, indexContactsConcurrently))
      .blockLast())
      .doesNotThrowAnyException()
  }

  private def contact(index: Int): ContactFields =
    ContactFields(new MailAddress(s"contact-$index@linagora.com"), firstname = s"Contact $index", surname = "User")
}
