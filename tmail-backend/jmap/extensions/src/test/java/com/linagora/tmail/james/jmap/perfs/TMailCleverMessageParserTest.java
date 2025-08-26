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

package com.linagora.tmail.james.jmap.perfs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.james.jmap.method.SystemZoneIdProvider;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TMailCleverMessageParserTest {
    private static final String TEXT_CONTENT = "text content";
    private static final String HTML_CONTENT = "<b>html</b> content";
    private static final String ATTACHMENT_CONTENT = "attachment content";

    private BodyPartBuilder htmlPart;
    private BodyPartBuilder textPart;
    private BodyPartBuilder textAttachment;
    private TMailCleverMessageParser testee;

    @BeforeEach
    void setup() throws IOException {
        testee = new TMailCleverMessageParser(new SystemZoneIdProvider());
        textPart = BodyPartBuilder.create().setBody(TEXT_CONTENT, "plain", StandardCharsets.UTF_8);
        htmlPart = BodyPartBuilder.create().setBody(HTML_CONTENT, "html", StandardCharsets.UTF_8);
        textAttachment = BodyPartBuilder.create()
            .setBody(ATTACHMENT_CONTENT, "plain", StandardCharsets.UTF_8)
            .setContentDisposition("attachment");
    }

    @Test
    void retrieveAttachmentsShouldBeEmptyWhenNoAttachment() throws Exception {
        Message message = Message.Builder
            .of()
            .setBody(TEXT_CONTENT, StandardCharsets.UTF_8)
            .build();

        assertThat(testee.retrieveAttachments(message)).isEmpty();
    }

    @Test
    void getAttachmentsShouldRetrieveAttachmentWhenMixedMultipart() {
        Multipart multipart = MultipartBuilder.create("mixed")
            .addBodyPart(textAttachment)
            .addBodyPart(htmlPart)
            .addBodyPart(textPart)
            .build();
        Message message = Message.Builder.of()
            .setBody(multipart)
            .build();

        assertThat(testee.retrieveAttachments(message))
            .hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveAttachmentWhenAlternativeMultipart() {
        BodyPart multipartAlternative = BodyPartBuilder.create()
            .setBody(MultipartBuilder.create("alternative")
                .addBodyPart(htmlPart)
                .addBodyPart(textPart)
                .build())
            .build();
        Multipart multipartMixed = MultipartBuilder.create("mixed")
            .addBodyPart(multipartAlternative)
            .addBodyPart(textAttachment)
            .build();

        Message message = Message.Builder.of()
            .setBody(multipartMixed)
            .build();

        assertThat(testee.retrieveAttachments(message))
            .hasSize(1);
    }

    @Test
    void getAttachmentsShouldRetrieveMultipleAttachmentsWhenMixedMultipart() {
        Multipart multipart = MultipartBuilder.create("mixed")
            .addBodyPart(textAttachment)
            .addBodyPart(textAttachment)
            .addBodyPart(htmlPart)
            .addBodyPart(textPart)
            .build();
        Message message = Message.Builder.of()
            .setBody(multipart)
            .build();

        assertThat(testee.retrieveAttachments(message))
            .hasSize(2);
    }

    @Test
    void getAttachmentsShouldRetrieveMultipleAttachmentsWhenAlternativeMultipart() {
        BodyPart multipartAlternative = BodyPartBuilder.create()
            .setBody(MultipartBuilder.create("alternative")
                .addBodyPart(htmlPart)
                .addBodyPart(textPart)
                .build())
            .build();
        Multipart multipartMixed = MultipartBuilder.create("mixed")
            .addBodyPart(multipartAlternative)
            .addBodyPart(textAttachment)
            .addBodyPart(textAttachment)
            .build();

        Message message = Message.Builder.of()
            .setBody(multipartMixed)
            .build();

        assertThat(testee.retrieveAttachments(message))
            .hasSize(2);
    }
}
