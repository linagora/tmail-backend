package com.linagora.tmail.james.app;

import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMapper;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.model.StringBackedAttachmentId;

public class TmailCleverAttachmentIdAssignationStrategy  implements CassandraAttachmentMapper.AttachmentIdAssignationStrategy {
    @Override
    public AttachmentId assign(ParsedAttachment parsedAttachment, MessageId messageId) {
        if (parsedAttachment instanceof TMailCleverParsedAttachment TMailCleverParsedAttachment) {
            return StringBackedAttachmentId.from(TMailCleverParsedAttachment.translate(messageId));
        }
        return StringBackedAttachmentId.random();
    }
}
