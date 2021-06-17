package com.linagora.tmail.pgp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.draft.utils.JsoupHtmlTextExtractor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.linagora.tmail.pgp.ClearEmailContentExtractor.ClearEmailContent;

public class ClearEmailContentExtractorTest {
    private static final String TEXT_CONTENT = "text content";
    private static final String HTML_CONTENT = "<b>html</b> content";
    private static final String BINARY_CONTENT = "binary";
    private static final String ATTACHMENT_CONTENT = "attachment content";

    private ClearEmailContentExtractor testee;

    private Preview.Factory previewFactory;
    private BodyPartBuilder htmlPart;
    private BodyPartBuilder textPart;
    private BodyPartBuilder textAttachment;

    @BeforeEach
    void setUp() throws IOException {
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();
        testee = new ClearEmailContentExtractor(htmlTextExtractor);
        previewFactory = new Preview.Factory(new MessageContentExtractor(), htmlTextExtractor);

        textPart = BodyPartBuilder.create().setBody(TEXT_CONTENT, "plain", StandardCharsets.UTF_8);
        htmlPart = BodyPartBuilder.create().setBody(HTML_CONTENT, "html", StandardCharsets.UTF_8);
        textAttachment = BodyPartBuilder.create()
            .setBody(ATTACHMENT_CONTENT, "plain", StandardCharsets.UTF_8)
            .setContentDisposition("attachment");
    }

    @Test
    void extractShouldSuccessWhenBinaryContentOnly() throws IOException {
        Message message = Message.Builder.of()
            .setBody(BasicBodyFactory.INSTANCE.binaryBody(BINARY_CONTENT, StandardCharsets.UTF_8))
            .build();

        assertThat(testee.extract(message))
            .isEqualTo(ClearEmailContent.builder()
                .html("")
                .preview(Preview.EMPTY)
                .attachments(ImmutableList.of()));
    }

    @Test
    void extractShouldSuccessWhenMultipartMixed() throws IOException {
        Multipart multipart = MultipartBuilder.create("mixed")
            .addBodyPart(textAttachment)
            .addBodyPart(htmlPart)
            .addBodyPart(textPart)
            .build();
        Message message = Message.Builder.of()
            .setBody(multipart)
            .build();
        ClearEmailContent clearEmailContent = testee.extract(message);

        assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(clearEmailContent.getHtml())
                .isEqualTo(HTML_CONTENT);
            softly.assertThat(clearEmailContent.getPreview())
                .isEqualTo(previewFactory.fromMime4JMessage(message));
            softly.assertThat(clearEmailContent.isHasAttachment())
                .isTrue();
            softly.assertThat(clearEmailContent.getAttachments()
                .get(0)
                .getContent()
                .contentEquals(ByteSource.wrap(ATTACHMENT_CONTENT.getBytes(StandardCharsets.UTF_8))))
                .isTrue();
        }));
    }

    @Test
    void attachmentShouldBeReturnEmptyWhenMessageHaveNotAttachment() throws IOException {
        Message message = Message.Builder.of()
            .setSubject("Subject")
            .setBody("small message", StandardCharsets.UTF_8)
            .build();
        ClearEmailContent clearEmailContent = testee.extract(message);
        assertThat(clearEmailContent.isHasAttachment())
            .isFalse();
        assertThat(clearEmailContent.getAttachments())
            .isEmpty();
    }

    @Test
    void attachmentShouldBeReturnValueWhenMessageHasSingleAttachment() throws IOException {
        Multipart multipart = MultipartBuilder.create("mixed")
            .addBodyPart(textAttachment)
            .addBodyPart(htmlPart)
            .addBodyPart(textPart)
            .build();
        Message message = Message.Builder.of()
            .setBody(multipart)
            .build();

        ClearEmailContent actual = testee.extract(message);
        assertThat(actual.isHasAttachment())
            .isEqualTo(true);
        assertThat(actual.getAttachments())
            .hasSize(1);
    }

    @Test
    void attachmentShouldBeReturnValueWhenMessageHasMultiAttachments() throws IOException {
        MultipartBuilder multipartBuilder = MultipartBuilder.create("mixed");
        IntStream.range(1, 10)
            .forEach(any -> multipartBuilder.addBodyPart(textAttachment));

        Message message = Message.Builder.of()
            .setBody(multipartBuilder.build())
            .build();

        ClearEmailContent actual = testee.extract(message);
        assertThat(actual.isHasAttachment())
            .isEqualTo(true);
        assertThat(actual.getAttachments())
            .hasSize(multipartBuilder.getBodyParts().size());
    }

    @Test
    void htmlShouldBeReturnEmptyWhenTextOnlyBody() throws IOException {
        Message message = Message.Builder.of()
            .setBody(TEXT_CONTENT, StandardCharsets.UTF_8)
            .build();
        assertThat(testee.extract(message).getHtml())
            .isEqualTo("");
    }

    @Test
    void htmlShouldBeReturnValueWhenHtmlOnlyBody() throws IOException {
        Message message = Message.Builder.of()
            .setBody(HTML_CONTENT, "html", StandardCharsets.UTF_8)
            .build();
        assertThat(testee.extract(message).getHtml())
            .isEqualTo(HTML_CONTENT);
    }

    @Test
    void htmlShouldBeReturnValueWhenMultipartAlternative() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
            .addBodyPart(textPart)
            .addBodyPart(htmlPart)
            .build();
        Message message = Message.Builder.of()
            .setBody(multipart)
            .build();
        assertThat(testee.extract(message).getHtml())
            .isEqualTo(HTML_CONTENT);
    }

    @Test
    void previewShouldBeReturnValueWhenMultipartAlternative() throws IOException {
        Multipart multipart = MultipartBuilder.create("alternative")
            .addBodyPart(textPart)
            .addBodyPart(htmlPart)
            .build();
        Message message = Message.Builder.of()
            .setBody(multipart)
            .build();
        assertThat(testee.extract(message).getPreview())
            .isEqualTo(previewFactory.fromMime4JMessage(message));
    }

    @Test
    void previewShouldBeReturnEmptyWhenBodyIsEmpty() throws IOException {
        Message message = Message.Builder.of()
            .setSubject("subject 123")
            .setBody("", StandardCharsets.UTF_8)
            .build();
        assertThat(testee.extract(message).getPreview())
            .isEqualTo(Preview.EMPTY);
    }

    @Test
    void previewShouldBeReturnValueWhenHtmlOnlyBody() throws IOException {
        Message message = Message.Builder.of()
            .setBody(HTML_CONTENT, "html", StandardCharsets.UTF_8)
            .build();
        assertThat(testee.extract(message).getPreview())
            .isEqualTo(previewFactory.fromMime4JMessage(message));
    }

    @Test
    void previewShouldBeReturnValueWhenTextOnlyBody() throws IOException {
        Message message = Message.Builder.of()
            .setBody(TEXT_CONTENT, StandardCharsets.UTF_8)
            .build();
        assertThat(testee.extract(message).getPreview())
            .isEqualTo(previewFactory.fromMime4JMessage(message));
    }

    @Test
    void previewShouldBeReturnValueWhenBigHtmlBody() throws IOException {
        Message message = Message.Builder.of()
            .setBody(HTML_CONTENT.repeat(999999), "html", StandardCharsets.UTF_8)
            .build();
        assertThat(testee.extract(message).getPreview())
            .isEqualTo(previewFactory.fromMime4JMessage(message));
    }
}
