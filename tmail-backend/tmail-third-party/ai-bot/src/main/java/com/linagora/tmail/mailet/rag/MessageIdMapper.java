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
package com.linagora.tmail.mailet.rag;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.utils.MimeMessageHeadersUtil;

class MessageIdMapper {
    private HashMap<MimeMessageId, MessageId> toInternal;
    private HashMap<MessageId, MimeMessageId> toMime;
    
    private MessageIdMapper() {
        this.toInternal = new HashMap<>();
        this.toMime = new HashMap<>();
    }

    /**
     * Build a MessageIdMapper from messages with fetched headers.
     */
    public MessageIdMapper(List<MessageResult> messages) throws MailboxException {
        this();
        for (MessageResult message : messages) {
            boolean headersOk = addMessage(message);
            if (!headersOk) {
                // TODO: log or throw errors
            }
        }
    }

    private boolean addMessage(MessageResult message) throws MailboxException {
        Optional<MimeMessageId> mimeIdOpt = parseMimeMessageId(message.getHeaders());
        if (!mimeIdOpt.isPresent()) {
            return false;
        }
        MimeMessageId mimeId = mimeIdOpt.get();
        MessageId id = message.getMessageId();

        toInternal.put(mimeId, id);
        toMime.put(id, mimeId);

        return true;
    }

    public MessageId toMessageId(MimeMessageId mimeMessageId) {
        return toInternal.get(mimeMessageId);
    }

    public MimeMessageId toMimeMessageId(MessageId messageId) {
        return toMime.get(messageId);
    }

    private static Optional<String> findHeaderValue(Headers headers, String headerName) throws MailboxException {
        // Necessary because James HeadersImpl is just a List<Header> under the hood.
        for (Header header : (Iterable<Header>) headers::headers) {
            if (header.getName() == headerName) {
                return Optional.of(header.getValue());
            }
        }
        return Optional.empty();
    }

    public static Optional<MimeMessageId> parseMimeMessageId(Headers headers) throws MailboxException {
        return findHeaderValue(headers, "Message-ID").map(s -> new MimeMessageId(s));
    }

    public static Optional<MimeMessageId> parseInReplyTo(Headers headers) throws MailboxException {
        return findHeaderValue(headers, "In-Reply-To").map(s -> new MimeMessageId(s));
    }
}