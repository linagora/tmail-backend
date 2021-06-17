package com.linagora.tmail.pgp;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import org.apache.james.jmap.api.model.Preview;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;

import com.google.common.base.Preconditions;

public class ClearEmailContentExtractor {

    public static class ClearEmailContent {
        interface Builder {

            @FunctionalInterface
            interface RequireHtml {
                RequirePreview html(String html);
            }

            @FunctionalInterface
            interface RequirePreview {
                RequireAttachments preview(Preview preview);
            }

            @FunctionalInterface
            interface RequireAttachments {
                ClearEmailContent attachments(List<ParsedAttachment> attachments);
            }
        }

        public static Builder.RequireHtml builder() {
            return html -> preview -> attachments
                -> new ClearEmailContent(html, preview, !attachments.isEmpty(), attachments);
        }

        private Preview preview;
        private boolean hasAttachment;
        private String html;
        private List<ParsedAttachment> attachments;

        ClearEmailContent(String html, Preview preview,
                          boolean hasAttachment,
                          List<ParsedAttachment> attachments) {
            Preconditions.checkNotNull(preview);
            Preconditions.checkNotNull(html);
            Preconditions.checkNotNull(attachments);
            this.preview = preview;
            this.hasAttachment = hasAttachment;
            this.html = html;
            this.attachments = attachments;
        }

        public Preview getPreview() {
            return preview;
        }

        public boolean isHasAttachment() {
            return hasAttachment;
        }

        public String getHtml() {
            return html;
        }

        public List<ParsedAttachment> getAttachments() {
            return attachments;
        }

        @Override
        public final boolean equals(Object other) {
            if (other instanceof ClearEmailContent) {
                ClearEmailContent that = (ClearEmailContent) other;
                return hasAttachment == that.hasAttachment &&
                    Objects.equals(preview, that.preview) &&
                    Objects.equals(html, that.html) &&
                    Objects.equals(attachments, that.attachments);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(preview, hasAttachment, html, attachments);
        }
    }

    private final MessageParser messageParser;
    private final MessageContentExtractor messageContentExtractor;
    private final Preview.Factory previewFactory;

    @Inject
    public ClearEmailContentExtractor(HtmlTextExtractor htmlTextExtractor) {
        this.messageParser = new MessageParser();
        this.messageContentExtractor = new MessageContentExtractor();
        this.previewFactory = new Preview.Factory(this.messageContentExtractor, htmlTextExtractor);
    }

    public ClearEmailContent extract(Message message) throws IOException {
        return ClearEmailContent.builder()
            .html(messageContentExtractor.extract(message)
                .getHtmlBody()
                .orElse(""))
            .preview(previewFactory.fromMime4JMessage(message))
            .attachments(messageParser.retrieveAttachments(message));
    }
}
