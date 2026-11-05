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

package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.FilterSerializer
import com.linagora.tmail.james.jmap.model.Update
import org.apache.james.core.Username
import org.apache.james.mailbox.MailboxSessionUtil
import org.apache.james.mailbox.inmemory.InMemoryId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import play.api.libs.json.Json

class FilterSetMethodSelfForwardTest {
  private val idFactory = new InMemoryId.Factory
  private val serializer = new FilterSerializer(idFactory)
  private val session = MailboxSessionUtil.create(Username.of("bob@domain.tld"))
  // validateRules only reads the session user
  private val method = new FilterSetMethod(null, null, null, null, idFactory, null)

  private def updateForwardingTo(address: String): Update =
    serializer.deserializeFilterSetRequest(Json.parse(
      s"""{"accountId":"aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8","update":{"singleton":[{"id":"1","name":"r",
         |"condition":{"field":"subject","comparator":"contains","value":"q"},
         |"action":{"appendIn":{"mailboxIds":[]},"forwardTo":{"addresses":["$address"],"keepACopy":true}}}]}}""".stripMargin)).get
      .parseUpdate()("singleton").toOption.get

  @ParameterizedTest
  @ValueSource(strings = Array("bob@domain.tld", "bob@Domain.TLD", "BOB@domain.tld", "Bob@DOMAIN.tld"))
  def validateRulesShouldRejectAForwardToTheOwnAddressOfTheUser(address: String): Unit =
    assertThat(method.validateRules(updateForwardingTo(address), session).isLeft).isTrue

  @ParameterizedTest
  @ValueSource(strings = Array("alice@domain.tld", "bob@other.tld", "not an address"))
  def validateRulesShouldNotRejectOtherAddresses(address: String): Unit =
    assertThat(method.validateRules(updateForwardingTo(address), session).isRight).isTrue
}
