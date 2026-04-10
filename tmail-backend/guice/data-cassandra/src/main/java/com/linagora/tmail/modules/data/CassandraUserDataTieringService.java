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

package com.linagora.tmail.modules.data;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static org.apache.james.jmap.cassandra.change.tables.CassandraEmailChangeTable.ACCOUNT_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.USERNAME;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.cassandra.cache.BlobStoreCache;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.cassandra.table.CassandraThreadTable;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.util.streams.Limit;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.linagora.tmail.tiering.UserDataTieringService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class CassandraUserDataTieringService implements UserDataTieringService {

    private static final String EMAIL_CHANGE_TABLE = "email_change";
    private static final String MAILBOX_CHANGE_TABLE = "mailbox_change";
    private static final int LOW_CONCURRENCY = 2;

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement deleteEmailChanges;
    private final PreparedStatement deleteMailboxChanges;
    private final PreparedStatement deleteThreadByUser;
    private final MailboxManager mailboxManager;
    private final CassandraMessageIdDAO cassandraMessageIdDAO;
    private final CassandraMessageDAOV3 cassandraMessageDAOV3;
    private final CassandraAttachmentDAOV2 cassandraAttachmentDAOV2;
    private final MessageFastViewProjection messageFastViewProjection;
    private final BlobStoreCache blobStoreCache;

    @Inject
    public CassandraUserDataTieringService(CqlSession session,
                                           MailboxManager mailboxManager,
                                           CassandraMessageIdDAO cassandraMessageIdDAO,
                                           CassandraMessageDAOV3 cassandraMessageDAOV3,
                                           CassandraAttachmentDAOV2 cassandraAttachmentDAOV2,
                                           MessageFastViewProjection messageFastViewProjection,
                                           BlobStoreCache blobStoreCache) {
        this.executor = new CassandraAsyncExecutor(session);
        this.mailboxManager = mailboxManager;
        this.cassandraMessageIdDAO = cassandraMessageIdDAO;
        this.cassandraMessageDAOV3 = cassandraMessageDAOV3;
        this.cassandraAttachmentDAOV2 = cassandraAttachmentDAOV2;
        this.messageFastViewProjection = messageFastViewProjection;
        this.blobStoreCache = blobStoreCache;

        this.deleteEmailChanges = session.prepare(deleteFrom(EMAIL_CHANGE_TABLE)
            .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
            .build());

        this.deleteMailboxChanges = session.prepare(deleteFrom(MAILBOX_CHANGE_TABLE)
            .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
            .build());

        this.deleteThreadByUser = session.prepare(deleteFrom(CassandraThreadTable.TABLE_NAME)
            .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
            .build());
    }

    @Override
    public Mono<Void> tierUserData(Username username, Duration tiering) {
        Date tieringDate = Date.from(Instant.now().minus(tiering));
        AccountId accountId = AccountId.fromUsername(username);
        MailboxSession session = mailboxManager.createSystemSession(username);

        return clearChanges(accountId)
            .then(clearThreadGuessing(username))
            .then(clearOldMessageProjections(tieringDate, session));
    }

    private Mono<Void> clearChanges(AccountId accountId) {
        return executor.executeVoid(deleteEmailChanges.bind()
                .set(ACCOUNT_ID, accountId.getIdentifier(), TypeCodecs.TEXT))
            .then(executor.executeVoid(deleteMailboxChanges.bind()
                .set(ACCOUNT_ID, accountId.getIdentifier(), TypeCodecs.TEXT)));
    }

    private Mono<Void> clearThreadGuessing(Username username) {
        return executor.executeVoid(deleteThreadByUser.bind()
            .set(USERNAME, username.asString(), TypeCodecs.TEXT));
    }

    private Mono<Void> clearOldMessageProjections(Date tieringDate, MailboxSession session) {
        return mailboxManager.search(MailboxQuery.privateMailboxesBuilder(session).build(), session)
            .map(metaData -> (CassandraId) metaData.getId())
            .flatMap(mailboxId -> cassandraMessageIdDAO.retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()), LOW_CONCURRENCY)
            .filter(metadata -> metadata.getInternalDate()
                .map(d -> d.before(tieringDate))
                .orElse(false))
            .flatMap(this::applyTiering)
            .then();
    }

    private Mono<Void> applyTiering(CassandraMessageMetadata metadata) {
        CassandraMessageId messageId = (CassandraMessageId) metadata.getComposedMessageId().getComposedMessageId().getMessageId();

        Mono<Void> clearFastView = Mono.from(messageFastViewProjection.delete(messageId));

        Mono<Void> clearHeaderBlob = metadata.getHeaderContent()
            .map(blobId -> Mono.from(blobStoreCache.remove(blobId)))
            .orElse(Mono.empty());

        Mono<Void> clearAttachments = cassandraMessageDAOV3.retrieveMessage(messageId, FetchType.METADATA)
            .flatMapMany(representation ->  Flux.fromIterable(representation.getAttachments()))
            .flatMap(attachment -> cassandraAttachmentDAOV2.delete(attachment.getAttachmentId()))
            .then();

        return Mono.when(clearFastView, clearHeaderBlob, clearAttachments);
    }
}
