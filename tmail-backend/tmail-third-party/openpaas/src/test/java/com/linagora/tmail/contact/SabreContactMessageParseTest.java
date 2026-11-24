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

package com.linagora.tmail.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.linagora.tmail.james.jmap.contact.ContactFields;

import ezvcard.property.Address;

public class SabreContactMessageParseTest {

    @Test
    void parseValidPayloadShouldSucceed() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/contacts\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "owner": "principals\\/users\\/67e26ebbecd9f300255a9f80",
                "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nN:vwevwe;wf vwe;;;\\r\\nEMAIL;TYPE=Work:mailto:toto@tutu.com\\r\\nEND:VCARD\\r\\n"
            }""";

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> {
                assertThat(message.owner()).isEqualTo(new SabreContactMessage.Owner.UserOwner("67e26ebbecd9f300255a9f80"));
                assertThat(message.getVCardUid()).isEqualTo("3ffe56cc-9196-4266-bf17-4894d00350d4");
                assertThat(message.getMailAddresses()).containsExactly(new MailAddress("toto@tutu.com"));
                assertThat(message.getContactFields()).containsExactly(ContactFields.of(new MailAddress("toto@tutu.com"), "Tran Tung"));
            });
    }

    @Test
    void parsePayloadWithDomainOwnerShouldSucceed() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/contacts\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "owner": "principals\\/domains\\/67e26ebbecd9f300255a9f80",
                "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nN:vwevwe;wf vwe;;;\\r\\nEMAIL;TYPE=Work:mailto:toto@tutu.com\\r\\nEND:VCARD\\r\\n"
            }""";

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> {
                assertThat(message.owner()).isEqualTo(new SabreContactMessage.Owner.DomainOwner("67e26ebbecd9f300255a9f80"));
                assertThat(message.getVCardUid()).isEqualTo("3ffe56cc-9196-4266-bf17-4894d00350d4");
                assertThat(message.getMailAddresses()).containsExactly(new MailAddress("toto@tutu.com"));
            });
    }

    @Test
    void parsePayloadWithOldCardDataShouldExposeThePreviousAddresses() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/domain-members\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "owner": "principals\\/domains\\/67e26ebbecd9f300255a9f80",
                "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nEMAIL:toto@tutu.com\\r\\nEND:VCARD\\r\\n",
                "oldcarddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nEMAIL:toto@tutu.com\\r\\nEMAIL:mailto:old@tutu.com\\r\\nEND:VCARD\\r\\n"
            }""";

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> {
                assertThat(message.getMailAddresses()).containsExactly(new MailAddress("toto@tutu.com"));
                assertThat(message.getPreviousMailAddresses())
                    .containsExactly(new MailAddress("toto@tutu.com"), new MailAddress("old@tutu.com"));
            });
    }

    @Test
    void parsePayloadWithoutOldCardDataShouldHaveNoPreviousAddresses() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/domain-members\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "owner": "principals\\/domains\\/67e26ebbecd9f300255a9f80",
                "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nEMAIL:toto@tutu.com\\r\\nEND:VCARD\\r\\n"
            }""";

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> assertThat(message.getPreviousMailAddresses()).isEmpty());
    }

    @Test
    void parsePayloadWithUnreadableOldCardDataShouldIgnoreIt() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/domain-members\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "owner": "principals\\/domains\\/67e26ebbecd9f300255a9f80",
                "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nEMAIL:toto@tutu.com\\r\\nEND:VCARD\\r\\n",
                "oldcarddata": "not a vcard"
            }""";

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> {
                assertThat(message.getMailAddresses()).containsExactly(new MailAddress("toto@tutu.com"));
                assertThat(message.getPreviousMailAddresses()).isEmpty();
            });
    }

    @Test
    void parsePayloadShouldSkipInvalidPreviousAddresses() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/domain-members\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "owner": "principals\\/domains\\/67e26ebbecd9f300255a9f80",
                "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nEMAIL:toto@tutu.com\\r\\nEND:VCARD\\r\\n",
                "oldcarddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nEMAIL:not an address\\r\\nEMAIL:old@tutu.com\\r\\nEND:VCARD\\r\\n"
            }""";

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> assertThat(message.getPreviousMailAddresses()).containsExactly(new MailAddress("old@tutu.com")));
    }

    @Test
    void parsePayloadWithUnknownOwnerTypeShouldReturnEmpty() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/contacts\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "owner": "principals\\/groups\\/67e26ebbecd9f300255a9f80",
                "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nN:vwevwe;wf vwe;;;\\r\\nEMAIL;TYPE=Work:mailto:toto@tutu.com\\r\\nEND:VCARD\\r\\n"
            }""";

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage)).isEmpty();
    }

    @Test
    void parsePayloadWithMissingCardDataShouldReturnEmpty() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/contacts\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "owner": "principals\\/users\\/67e26ebbecd9f300255a9f80"
            }""";

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage)).isEmpty();
    }

    @Test
    void parsePayloadWithMissingOwnerShouldReturnEmpty() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/contacts\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nN:vwevwe;wf vwe;;;\\r\\nEMAIL;TYPE=Work:mailto:toto@tutu.com\\r\\nEND:VCARD\\r\\n"
            }""";
        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage)).isEmpty();
    }

    @Test
    void parsePayloadWithInvalidJsonShouldThrowException() {
        String amqpMessage = "{ invalid json }";

        assertThatCode(() -> SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isInstanceOf(SabreContactMessage.SabreContactParseException.class)
            .hasMessageContaining("Failed to parse contact message");
    }

    @Test
    void parsePayloadWithInvalidVCardShouldThrowException() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/contacts\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "owner": "principals\\/users\\/67e26ebbecd9f300255a9f80",
                "carddata": "BEGIN:Invalid"
            }""";

        assertThatCode(() -> SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isInstanceOf(SabreContactMessage.SabreContactParseException.class);
    }

    @Test
    void parsePayloadWithMissingEmailInVCardShouldReturnEmptyMailAddresses() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/contacts\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                "owner": "principals\\/users\\/67e26ebbecd9f300255a9f80",
                "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nN:vwevwe;wf vwe;;;\\r\\nEND:VCARD\\r\\n"
            }""";

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> {
                assertThat(message.getMailAddresses()).isEmpty();
            });
    }

    @Test
    void parsePayloadWithMultiEmailInVCardShouldSucceed() {
        String amqpMessage = """
            {
                 "path": "addressbooks\\/67eb704d55f66500603baff0\\/contacts\\/a7f876d3-11d2-4910-9ff4-19cd9dbfab7e.vcf",
                 "owner": "principals\\/users\\/67eb704d55f66500603baff0",
                 "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:a7f876d3-11d2-4910-9ff4-19cd9dbfab7e\\r\\nFN:Tung Tran\\r\\nN:Tran;Tung;;;\\r\\nEMAIL;TYPE=Work:mailto:tung1@gmail.com\\r\\nEMAIL;TYPE=Home:mailto:tung2@gmail.com\\r\\nEMAIL;TYPE=Other:mailto:tung3@gmail.com\\r\\nEND:VCARD\\r\\n"
             }
            """;
        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> assertThat(message.getMailAddresses())
                .containsExactlyInAnyOrder(new MailAddress("tung1@gmail.com"),
                    new MailAddress("tung2@gmail.com"), new MailAddress("tung3@gmail.com")));
    }

    @Test
    void getContactFieldsShouldNotRepeatAnAddressListedTwice() {
        String amqpMessage = """
            {
                "path": "addressbooks\\/67ef5\\/contacts\\/1.vcf",
                "owner": "principals\\/users\\/67ef5",
                "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:a\\r\\nFN:Tung\\r\\nEMAIL;TYPE=work:tung@example.com\\r\\nEMAIL;TYPE=home:tung@example.com\\r\\nEND:VCARD\\r\\n"
            }""";

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> assertThat(message.getContactFields())
                .extracting(fields -> fields.address().asString())
                .containsExactly("tung@example.com"));
    }

    private static String amqpMessage(String cardData, Optional<String> oldCardData) throws Exception {
        Map<String, String> message = new HashMap<>();
        message.put("path", "addressbooks/67e26ebbecd9f300255a9f80/contacts/3ffe56cc.vcf");
        message.put("owner", "principals/users/67e26ebbecd9f300255a9f80");
        message.put("carddata", cardData);
        oldCardData.ifPresent(old -> message.put("oldcarddata", old));
        return new ObjectMapper().writeValueAsString(message);
    }

    private static String card(String properties) {
        return "BEGIN:VCARD\r\nVERSION:3.0\r\nUID:3ffe56cc\r\nFN:Tran Tung\r\nEMAIL:toto@tutu.com\r\n" + properties + "END:VCARD\r\n";
    }

    private static String labelBomb() {
        return card("ADR:\r\n".repeat(20_000) + "LABEL;TYPE=x:\r\n".repeat(20_000));
    }

    @Test
    void parsePayloadShouldRejectACardTooCostlyToMatchItsLabels() throws Exception {
        String amqpMessage = amqpMessage(labelBomb(), Optional.empty());

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            assertThatThrownBy(() -> SabreContactMessage.parseAMQPMessage(amqpMessage))
                .isInstanceOf(SabreContactMessage.SabreContactParseException.class));
    }

    @Test
    void parsePayloadShouldRejectAnOversizedCard() throws Exception {
        String amqpMessage = amqpMessage(card("NOTE:" + "a".repeat(SabreContactMessage.MAX_CARD_DATA_LENGTH) + "\r\n"), Optional.empty());

        assertThatThrownBy(() -> SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isInstanceOf(SabreContactMessage.SabreContactParseException.class);
    }

    @Test
    void parsePayloadShouldIgnoreAPreviousCardTooCostlyToParse() throws Exception {
        String amqpMessage = amqpMessage(card(""), Optional.of(labelBomb()));

        assertThat(assertTimeoutPreemptively(Duration.ofSeconds(5), () -> SabreContactMessage.parseAMQPMessage(amqpMessage)))
            .isPresent()
            .get()
            .satisfies(message -> assertThat(message.getPreviousMailAddresses()).isEmpty());
    }

    @Test
    void parsePayloadShouldStillMatchTheLabelsOfARegularCard() throws Exception {
        String amqpMessage = amqpMessage(card("""
            item1.ADR;TYPE=home:;;1 rue A;Paris;;75001;France\r
            ADR;TYPE=work:;;2 rue B;Lyon;;69001;France\r
            LABEL;TYPE=work:2 rue B\\nLyon\r
            item1.LABEL;TYPE=home:1 rue A\\nParis\r
            """), Optional.empty());

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> assertThat(message.vcard().getAddresses())
                .extracting(Address::getLabel)
                .containsExactly("1 rue A\nParis", "2 rue B\nLyon"));
    }

    @Test
    void parseErrorsShouldNotHoldTheCardData() throws Exception {
        String amqpMessage = amqpMessage("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Secret Person\r\nEMAIL:secret@tutu.com\r\nEND:VCARD\r\n", Optional.empty());

        assertThatThrownBy(() -> SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isInstanceOf(SabreContactMessage.SabreContactParseException.class)
            .satisfies(e -> assertThat(Throwables.getCausalChain(e))
                .extracting(Throwable::getMessage)
                .noneMatch(message -> message != null && message.contains("secret@tutu.com")));
    }

    @Test
    void checkCanBeParsedShouldCountGroupedProperties() {
        String grouped = card("item1.ADR:\r\n".repeat(200) + "item2.LABEL:\r\n".repeat(200));

        assertThatThrownBy(() -> SabreContactMessage.checkCanBeParsed(grouped))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsePayloadShouldRejectALabelBombWithFoldedPropertyNames() throws Exception {
        String folded = card("AD\r\n R:\r\n".repeat(20_000) + "LAB\r\n\tEL;TYPE=x:\r\n".repeat(20_000));
        String amqpMessage = amqpMessage(folded, Optional.empty());

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            assertThatThrownBy(() -> SabreContactMessage.parseAMQPMessage(amqpMessage))
                .isInstanceOf(SabreContactMessage.SabreContactParseException.class));
    }

    @Test
    void parsePayloadShouldRejectALabelBombNestedInAnAgentCard() throws Exception {
        String nested = "BEGIN:VCARD\\nVERSION:3.0\\n" + "ADR:\\n".repeat(20_000) + "LABEL;TYPE=x:\\n".repeat(20_000) + "END:VCARD";
        String amqpMessage = amqpMessage(card("AGENT:" + nested + "\r\n"), Optional.empty());

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            assertThatThrownBy(() -> SabreContactMessage.parseAMQPMessage(amqpMessage))
                .isInstanceOf(SabreContactMessage.SabreContactParseException.class));
    }

    @Test
    void checkCanBeParsedShouldRejectAGroupedAgent() {
        assertThatThrownBy(() -> SabreContactMessage.checkCanBeParsed(card("item1.agent:BEGIN:VCARD\\nEND:VCARD\r\n")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsePayloadShouldIgnoreAPreviousCardWithAnAgent() throws Exception {
        String amqpMessage = amqpMessage(card(""), Optional.of(card("AGENT:BEGIN:VCARD\\nVERSION:3.0\\nFN:x\\nEND:VCARD\r\n")));

        assertThat(SabreContactMessage.parseAMQPMessage(amqpMessage))
            .isPresent()
            .get()
            .satisfies(message -> assertThat(message.getPreviousMailAddresses()).isEmpty());
    }

    @Test
    void jsonParseErrorsShouldNotQuoteThePayload() {
        String truncated = "{\"owner\": \"principals/users/1\", \"carddata\": \"EMAIL:secret@tutu.com\" \"broken";

        assertThatThrownBy(() -> SabreContactMessage.parseAMQPMessage(truncated))
            .isInstanceOf(SabreContactMessage.SabreContactParseException.class)
            .satisfies(e -> assertThat(Throwables.getCausalChain(e))
                .extracting(Throwable::getMessage)
                .noneMatch(message -> message != null && message.contains("secret@tutu.com")));
    }
}
