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
 *******************************************************************/

package com.linagora.tmail.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IsMainRecipientTest {
    private static final String RECIPIENT1 = "abc1@example.org";
    private static final String RECIPIENT2 = "abc2@example.org";
    private static final String RECIPIENT3 = "abc3@example.org";
    private MailAddress recipient1;
    private MailAddress recipient2;
    private MailAddress recipient3;

    private IsMainRecipient testee;

    @BeforeEach
    void setUp() throws AddressException {
        testee = new IsMainRecipient();
        recipient1 = new MailAddress(RECIPIENT1);
        recipient2 = new MailAddress(RECIPIENT2);
        recipient3 = new MailAddress(RECIPIENT3);
    }

    @Test
    void matchShouldReturnOnlyRecipientsPresentInToHeader() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(recipient1, recipient2, recipient3)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient(recipient1.asString())
                .addCcRecipient(recipient2.asString())
                .addBccRecipient(recipient3.asString()))
            .build();

        Collection<MailAddress> matched = testee.match(fakeMail);

        assertThat(matched).containsOnly(recipient1);
    }

    @Test
    void matchShouldSupportMultipleToRecipients() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(recipient1, recipient2, recipient3)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient(recipient1.asString(), recipient2.asString())
                .addCcRecipient(recipient3.asString()))
            .build();

        Collection<MailAddress> matched = testee.match(fakeMail);

        assertThat(matched).containsOnly(recipient1, recipient2);
    }

    @Test
    void matchShouldSupportDisplayNameInToHeader() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(recipient1)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient("Alice <" + recipient1.asString() + ">"))
            .build();

        Collection<MailAddress> matched = testee.match(fakeMail);

        assertThat(matched).containsOnly(recipient1);
    }

    @Test
    void matchShouldReturnEmptyRecipientsWhenNoToHeader() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(recipient1)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();

        Collection<MailAddress> matched = testee.match(fakeMail);

        assertThat(matched).isEmpty();
    }

    @Test
    void matchShouldReturnEmptyRecipientsWhenEmptyToHeader() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(recipient1)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("To", ""))
            .build();

        Collection<MailAddress> matched = testee.match(fakeMail);

        assertThat(matched).isEmpty();
    }

    @Test
    void matchShouldBeCaseInsensitive() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(new MailAddress(RECIPIENT1.toLowerCase()))
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient(RECIPIENT1.toUpperCase()))
            .build();

        Collection<MailAddress> matched = testee.match(fakeMail);

        assertThat(matched).containsOnly(recipient1);
    }

    @Test
    void matchShouldSupportMultipleToHeaders() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(recipient1, recipient2)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("To", recipient1.asString())
                .addHeader("To", recipient2.asString()))
            .build();

        Collection<MailAddress> matched = testee.match(fakeMail);

        assertThat(matched).containsOnly(recipient1, recipient2);
    }

}
