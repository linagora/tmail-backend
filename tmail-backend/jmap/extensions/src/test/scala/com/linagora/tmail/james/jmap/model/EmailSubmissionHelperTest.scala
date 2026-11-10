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

package com.linagora.tmail.james.jmap.model

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Properties

import com.linagora.tmail.james.jmap.model.EmailSubmissionHelper.{addresses, senderAddresses}
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

class EmailSubmissionHelperTest {
  @Test
  def addressesShouldReturnTheAddresses(): Unit =
    assertThat(addresses("Alice <alice@domain.tld>, bob@domain.tld").asJava)
      .containsExactly("alice@domain.tld", "bob@domain.tld")

  @Test
  def addressesShouldReturnAnAddressAfterAnAngleAddress(): Unit =
    assertThat(addresses("<alice@domain.tld> bob@domain.tld").asJava)
      .containsExactly("alice@domain.tld", "bob@domain.tld")

  @Test
  def addressesShouldReturnWhatMime4jReadsAfterAComment(): Unit =
    assertThat(addresses("alice@domain.tld (c) bob@domain.tld").asJava)
      .containsExactly("alice@domain.tld", "alice@domain.tldbob@domain.tld")

  @Test
  def addressesShouldReturnTheDomainFollowingAComment(): Unit =
    assertThat(addresses("alice@domain.tld(c).evil").asJava)
      .containsExactly("alice@domain.tld", "alice@domain.tld.evil")

  @Test
  def addressesShouldNotDuplicateAddressesDifferingByCase(): Unit =
    assertThat(addresses("Alice@Domain.tld").asJava)
      .containsExactly("Alice@Domain.tld")

  @Test
  def addressesShouldNotDuplicateGroupMembers(): Unit =
    assertThat(addresses("team: alice@domain.tld, bob@domain.tld;").asJava)
      .hasSize(1)

  @Test
  def addressesShouldReadTheWholeFieldWhenTheDisplayNameIsNotAscii(): Unit =
    assertThat(addresses("Éléonore <eleonore@linagora.com>").asJava)
      .containsExactly("eleonore@linagora.com")

  @Test
  def addressesShouldReturnAnAddressAfterAnAngleAddressWhenTheDisplayNameIsNotAscii(): Unit =
    assertThat(addresses("ầầầầầ <alice@domain.tld> bob@domain.tld").asJava)
      .containsExactly("alice@domain.tld", "bob@domain.tld")

  @Test
  def addressesShouldReadTheWholeFieldWhenAFoldedDisplayNameIsNotAscii(): Unit =
    assertThat(addresses("Éléonore\r\n <eleonore@linagora.com>").asJava)
      .containsExactly("eleonore@linagora.com")

  @Test
  def addressesShouldReadTheWholeFieldWhenItIsRawEightBit(): Unit =
    assertThat(addresses(rawEightBitMessage("From: Éric <eric@linagora.com>").getHeader("From", ",")).asJava)
      .containsExactly("eric@linagora.com")

  @Test
  def addressesShouldReturnAnAddressAfterAnAngleAddressWhenItIsRawEightBit(): Unit =
    assertThat(addresses(rawEightBitMessage("From: ầầầầầ <alice@domain.tld> bob@domain.tld").getHeader("From", ",")).asJava)
      .containsExactly("alice@domain.tld", "bob@domain.tld")

  @Test
  def senderAddressesShouldReadBothFields(): Unit =
    assertThat(senderAddresses(rawEightBitMessage("From: <alice@domain.tld> bob@domain.tld\r\nSender: carol@domain.tld(c).evil")).asJava)
      .containsExactly(
        "sender" -> Right("carol@domain.tld"),
        "sender" -> Right("carol@domain.tld.evil"),
        "from" -> Right("alice@domain.tld"),
        "from" -> Right("bob@domain.tld"))

  @Test
  def senderAddressesShouldReadEverySenderField(): Unit =
    assertThat(senderAddresses(rawEightBitMessage("From: alice@domain.tld\r\nSender: alice@domain.tld\r\nSender: bob@domain.tld")).asJava)
      .containsExactly(
        "sender" -> Right("alice@domain.tld"),
        "sender" -> Right("bob@domain.tld"),
        "from" -> Right("alice@domain.tld"))

  @Test
  def senderAddressesShouldReadTheSenderAsFromWhenFromIsMissing(): Unit =
    assertThat(senderAddresses(rawEightBitMessage("Sender: <alice@domain.tld> bob@domain.tld")).asJava)
      .containsExactly(
        "sender" -> Right("alice@domain.tld"),
        "sender" -> Right("bob@domain.tld"),
        "from" -> Right("alice@domain.tld"),
        "from" -> Right("bob@domain.tld"))

  @Test
  def senderAddressesShouldReturnAFieldThatDoesNotParse(): Unit =
    assertThat(senderAddresses(rawEightBitMessage("From: alice@domain.tld\r\nSender: alice@domain.tld>")).asJava)
      .containsExactly(
        "sender" -> Left("alice@domain.tld>"),
        "from" -> Right("alice@domain.tld"))

  // Jakarta Mail reads a raw 8-bit header as one char per byte
  private def rawEightBitMessage(headers: String): MimeMessage =
    new MimeMessage(Session.getInstance(new Properties()),
      new ByteArrayInputStream((headers + "\r\n\r\nbody\r\n").getBytes(UTF_8)))
}
