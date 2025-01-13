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

package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.contact.{Addition, ContactOwner, EmailAddressContactMessage, MessageEntry, User}
import org.apache.james.core.MailAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json}

class EmailAddressContactMessageSerializerTest {

  @Test
  def deserializeShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{ 
        |   "type": "addition",
        |   "scope": "user", 
        |   "owner" : "bob@domain.tld",
        |   "entry": {
        |        "address": "alice@domain.tld",
        |        "firstname": "Alice",
        |        "surname": "Watson"
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[EmailAddressContactMessage] = EmailAddressContactMessageSerializer.deserializeEmailAddressContactMessage(jsInput)
    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get)
      .isEqualTo(EmailAddressContactMessage(
        messageType = Addition,
        scope = User,
        owner = ContactOwner("bob@domain.tld"),
        entry = MessageEntry(
          address = new MailAddress("alice@domain.tld"),
          firstname = Some("Alice"),
          surname = Some("Watson"))))
  }

  @Test
  def deserializeShouldSuccessWhenMissingEntryFirstnameAndSurname(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |   "type": "addition",
        |   "scope": "user",
        |   "owner" : "bob@domain.tld",
        |   "entry": {
        |        "address": "alice@domain.tld"
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[EmailAddressContactMessage] = EmailAddressContactMessageSerializer.deserializeEmailAddressContactMessage(jsInput)
    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get)
      .isEqualTo(EmailAddressContactMessage(
        messageType = Addition,
        scope = User,
        owner = ContactOwner("bob@domain.tld"),
        entry = MessageEntry(
          address = new MailAddress("alice@domain.tld"),
          firstname = None,
          surname = None)))
  }

  @Test
  def deserializeShouldThrowWhenMissingEntryAddress(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |   "type": "addition",
        |   "scope": "user",
        |   "owner" : "bob@domain.tld",
        |   "entry": {
        |        "firstname": "Alice",
        |        "surname": "Watson"
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[EmailAddressContactMessage] = EmailAddressContactMessageSerializer.deserializeEmailAddressContactMessage(jsInput)
    assertThat(deserializeResult.isError)
      .isTrue
  }

  @Test
  def deserializeShouldThrowWhenMailAddressIsInvalid(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |   "type": "addition",
        |   "scope": "user",
        |   "owner" : "bob@domain.tld",
        |   "entry": {
        |        "address": "alice123",
        |        "firstname": "Alice",
        |        "surname": "Watson"
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[EmailAddressContactMessage] = EmailAddressContactMessageSerializer.deserializeEmailAddressContactMessage(jsInput)
    assertThat(deserializeResult.isError)
      .isTrue
  }

}
