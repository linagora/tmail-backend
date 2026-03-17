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

package com.linagora.tmail.james.jmap.projections;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.util.streams.Limit;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface KeywordEmailQueryView {
    record Options(Optional<Instant> before, Optional<Instant> after, Limit limit, boolean collapseThread) {
    }

    Mono<Void> save(Username username, Keyword keyword, Instant receivedAt, MessageId messageId, ThreadId threadId);

    Mono<Void> delete(Username username, Keyword keyword, Instant receivedAt, MessageId messageId);

    Flux<MessageId> listMessagesByKeyword(Username username, Keyword keyword, Options options);

    Mono<Void> truncate();
}
