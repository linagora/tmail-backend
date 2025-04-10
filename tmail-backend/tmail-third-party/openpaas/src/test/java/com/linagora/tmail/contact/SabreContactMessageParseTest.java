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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.contact.ContactFields;

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
                assertThat(message.openPaasUserId()).isEqualTo("67e26ebbecd9f300255a9f80");
                assertThat(message.getVCardUid()).isEqualTo("3ffe56cc-9196-4266-bf17-4894d00350d4");
                assertThat(message.getMailAddresses()).containsExactly(new MailAddress("toto@tutu.com"));
                assertThat(message.getContactFields()).containsExactly(ContactFields.of(new MailAddress("toto@tutu.com"), "Tran Tung"));
            });
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
}