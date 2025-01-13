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

import org.junit.jupiter.api.{BeforeEach, Disabled, Test}

class MemoryEmailAddressContactSearchEngineTest extends EmailAddressContactSearchEngineContract {
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
}
