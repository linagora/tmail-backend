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

package com.linagora.tmail.mailbox.opensearch;

import java.time.ZoneId;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import reactor.core.publisher.Mono;

/**
 * Indexes the text of messages in its composed form (NFC), see {@link UnicodeNormalization}.
 *
 * Normalizing the serialized document is safe: JSON syntax is ASCII and no ASCII character composes with a following
 * combining mark into a JSON structural character.
 */
public class TmailMessageToOpenSearchJson extends MessageToOpenSearchJson {
    public TmailMessageToOpenSearchJson(TextExtractor textExtractor, ZoneId zoneId, IndexAttachments indexAttachments, IndexHeaders indexHeaders) {
        super(textExtractor, zoneId, indexAttachments, indexHeaders);
    }

    @Inject
    public TmailMessageToOpenSearchJson(TextExtractor textExtractor, OpenSearchMailboxConfiguration configuration) {
        super(textExtractor, configuration);
    }

    @Override
    public Mono<String> convertToJson(MailboxMessage message, MailboxSession session) {
        return super.convertToJson(message, session)
            .map(UnicodeNormalization::nfc);
    }

    @Override
    public Mono<String> convertToJsonWithoutAttachment(MailboxMessage message, MailboxSession session) {
        return super.convertToJsonWithoutAttachment(message, session)
            .map(UnicodeNormalization::nfc);
    }

    @Override
    public Mono<String> convertToJson(MailboxMessage message) {
        return super.convertToJson(message)
            .map(UnicodeNormalization::nfc);
    }

    @Override
    public Mono<String> convertToJsonWithoutAttachment(MailboxMessage message) {
        return super.convertToJsonWithoutAttachment(message)
            .map(UnicodeNormalization::nfc);
    }
}
