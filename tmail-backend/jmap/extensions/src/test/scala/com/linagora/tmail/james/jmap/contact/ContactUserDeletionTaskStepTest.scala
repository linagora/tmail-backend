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

import ContactUsernameChangeTaskStepTest.{ALICE, ALICE_ACCOUNT_ID, ALICE_CONTACT, ANDRE_CONTACT, BOB_ACCOUNT_ID, MARIE_CONTACT}
import org.apache.james.core.Domain
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class ContactUserDeletionTaskStepTest {
  var searchEngine: EmailAddressContactSearchEngine = _
  var testee: ContactUserDeletionTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    searchEngine = new InMemoryEmailAddressContactSearchEngine
    testee = new ContactUserDeletionTaskStep(searchEngine)
  }

  @Test
  def shouldRemoveContacts(): Unit = {
    SMono.fromPublisher(searchEngine.index(ALICE_ACCOUNT_ID, ANDRE_CONTACT)).block()
    SMono.fromPublisher(searchEngine.index(ALICE_ACCOUNT_ID, MARIE_CONTACT)).block()

    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux.fromPublisher(searchEngine.list(ALICE_ACCOUNT_ID))
      .map(_.fields)
      .collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def shouldNotRemoveContactsOfOtherUsers(): Unit = {
    SMono.fromPublisher(searchEngine.index(BOB_ACCOUNT_ID, ANDRE_CONTACT)).block()
    SMono.fromPublisher(searchEngine.index(BOB_ACCOUNT_ID, MARIE_CONTACT)).block()

    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux.fromPublisher(searchEngine.list(BOB_ACCOUNT_ID))
      .map(_.fields)
      .collectSeq().block().asJava)
      .containsExactlyInAnyOrder(ANDRE_CONTACT, MARIE_CONTACT)
  }

  @Test
  def shouldBeIdempotent(): Unit = {
    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThatCode(() => SMono.fromPublisher(testee.deleteUserData(ALICE)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def shouldRemoveTheContactInDomainContact(): Unit = {
    SMono.fromPublisher(searchEngine.index(Domain.of("linagora.com"), ALICE_CONTACT)).block()
    SMono.fromPublisher(searchEngine.index(Domain.of("linagora.com"), ANDRE_CONTACT)).block()

    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux.fromPublisher(searchEngine.list(Domain.of("linagora.com")))
      .map(_.fields)
      .collectSeq().block().asJava)
      .containsOnly(ANDRE_CONTACT)
  }

}
