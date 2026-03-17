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
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryKeywordEmailQueryView implements KeywordEmailQueryView {
    private record Entry(Instant receivedAt, MessageId messageId, ThreadId threadId) {
    }

    private final Map<Username, Table<Keyword, MessageId, Entry>> entriesByUser;

    @Inject
    public MemoryKeywordEmailQueryView() {
        this.entriesByUser = new ConcurrentHashMap<>();
    }

    @Override
    public Mono<Void> save(Username username, Keyword keyword, Instant receivedAt, MessageId messageId, ThreadId threadId) {
        return Mono.fromRunnable(() -> userEntries(username)
            .put(keyword, messageId, new Entry(receivedAt, messageId, threadId)));
    }

    @Override
    public Mono<Void> delete(Username username, Keyword keyword, Instant receivedAt, MessageId messageId) {
        return Mono.fromRunnable(() -> {
            Entry entry = userEntries(username).get(keyword, messageId);
            if (entry != null && entry.receivedAt().equals(receivedAt)) {
                userEntries(username).remove(keyword, messageId);
            }
        });
    }

    @Override
    public Mono<Void> truncate() {
        return Mono.fromRunnable(entriesByUser::clear);
    }

    @Override
    public Flux<MessageId> listMessagesByKeyword(Username username, Keyword keyword, Options options) {
        Preconditions.checkArgument(!options.limit().isUnlimited(), "Limit should be defined");

        Flux<Entry> baseEntries = Flux.fromIterable(userEntries(username).row(keyword).values())
            .filter(beforeFiltering(options))
            .filter(afterFiltering(options));

        return maybeCollapseThreads(options.collapseThread()).apply(baseEntries)
            .sort(Comparator.comparing(Entry::receivedAt).reversed())
            .map(Entry::messageId)
            .take(options.limit().getLimit().get());
    }

    private Predicate<Entry> beforeFiltering(Options options) {
        return entry -> options.before()
            .map(before -> entry.receivedAt().isBefore(before))
            .orElse(true);
    }

    private Predicate<Entry> afterFiltering(Options options) {
        return entry -> options.after()
            .map(after -> entry.receivedAt().isAfter(after) || entry.receivedAt().equals(after))
            .orElse(true);
    }

    private Function<Flux<Entry>, Flux<Entry>> maybeCollapseThreads(boolean collapseThreads) {
        return entries -> {
            if (collapseThreads) {
                return entries.groupBy(Entry::threadId)
                    .flatMap(group -> group.reduce((entry1, entry2) ->
                        entry1.receivedAt().isAfter(entry2.receivedAt()) ? entry1 : entry2));
            }
            return entries;
        };
    }

    private Table<Keyword, MessageId, Entry> userEntries(Username username) {
        return entriesByUser.computeIfAbsent(username, unused -> Tables.synchronizedTable(HashBasedTable.create()));
    }
}
