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

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.util.streams.Limit;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryKeywordEmailQueryView implements KeywordEmailQueryView {
    private record Entry(ZonedDateTime receivedAt, MessageId messageId, ThreadId threadId) {
    }

    private final Map<Username, Table<Keyword, MessageId, Entry>> entriesByUser;

    @Inject
    public MemoryKeywordEmailQueryView() {
        this.entriesByUser = new ConcurrentHashMap<>();
    }

    @Override
    public Mono<Void> save(Username username, Keyword keyword, ZonedDateTime receivedAt, MessageId messageId, ThreadId threadId) {
        return Mono.fromRunnable(() -> userEntries(username)
            .put(keyword, messageId, new Entry(receivedAt, messageId, threadId)));
    }

    @Override
    public Mono<Void> delete(Username username, Keyword keyword, ZonedDateTime receivedAt, MessageId messageId) {
        return Mono.fromRunnable(() -> {
            Entry entry = userEntries(username).get(keyword, messageId);
            if (entry != null && entry.receivedAt.isEqual(receivedAt)) {
                userEntries(username).remove(keyword, messageId);
            }
        });
    }

    @Override
    public Flux<MessageId> listMessagesByKeyword(Username username, Keyword keyword, Limit limit, boolean collapseThreads) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        Flux<Entry> baseEntries = Flux.fromIterable(userEntries(username).row(keyword).values());

        return maybeCollapseThreads(collapseThreads).apply(baseEntries)
            .sort(Comparator.comparing(Entry::receivedAt).reversed())
            .map(entry -> entry.messageId)
            .take(limit.getLimit().get());
    }

    @Override
    public Flux<MessageId> listMessagesByKeywordSinceAfter(Username username, Keyword keyword, ZonedDateTime since, Limit limit, boolean collapseThreads) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        Flux<Entry> baseEntries = Flux.fromIterable(userEntries(username).row(keyword).values())
            .filter(entry -> entry.receivedAt.isAfter(since) || entry.receivedAt.isEqual(since));

        return maybeCollapseThreads(collapseThreads).apply(baseEntries)
            .sort(Comparator.comparing(Entry::receivedAt).reversed())
            .map(entry -> entry.messageId)
            .take(limit.getLimit().get());
    }

    @Override
    public Flux<MessageId> listMessagesByKeywordBefore(Username username, Keyword keyword, ZonedDateTime before, Limit limit, boolean collapseThreads) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        Flux<Entry> baseEntries = Flux.fromIterable(userEntries(username).row(keyword).values())
            .filter(entry -> entry.receivedAt.isBefore(before));

        return maybeCollapseThreads(collapseThreads).apply(baseEntries)
            .sort(Comparator.comparing(Entry::receivedAt).reversed())
            .map(entry -> entry.messageId)
            .take(limit.getLimit().get());
    }

    private Function<Flux<Entry>, Flux<Entry>> maybeCollapseThreads(boolean collapseThreads) {
        return entries -> {
            if (collapseThreads) {
                return entries.groupBy(entry -> entry.threadId)
                    .flatMap(group -> group.reduce((entry1, entry2) ->
                        entry1.receivedAt.isAfter(entry2.receivedAt) ? entry1 : entry2));
            }
            return entries;
        };
    }

    private Table<Keyword, MessageId, Entry> userEntries(Username username) {
        return entriesByUser.computeIfAbsent(username, unused -> Tables.synchronizedTable(HashBasedTable.create()));
    }
}
