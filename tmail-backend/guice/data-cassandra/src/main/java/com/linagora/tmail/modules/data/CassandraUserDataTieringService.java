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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.MetricableBlobStore;
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
import org.apache.james.mailbox.cassandra.mail.CassandraThreadDAO;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.utils.MimeMessageHeadersUtil;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.streams.Limit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.google.common.hash.Hashing;
import com.linagora.tmail.blob.guice.BlobStoreCacheCleaner;
import com.linagora.tmail.tiering.UserDataTieringContext;
import com.linagora.tmail.tiering.UserDataTieringService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class CassandraUserDataTieringService implements UserDataTieringService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraUserDataTieringService.class);
    private static final String EMAIL_CHANGE_TABLE = "email_change";
    private static final String MAILBOX_CHANGE_TABLE = "mailbox_change";
    private static final int LOW_CONCURRENCY = 8;

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement deleteEmailChanges;
    private final PreparedStatement deleteMailboxChanges;
    private final MailboxManager mailboxManager;
    private final CassandraMessageIdDAO cassandraMessageIdDAO;
    private final CassandraMessageDAOV3 cassandraMessageDAOV3;
    private final CassandraAttachmentDAOV2 cassandraAttachmentDAOV2;
    private final CassandraThreadDAO cassandraThreadDAO;
    private final MessageFastViewProjection messageFastViewProjection;
    private final BlobStoreCacheCleaner blobStoreCacheCleaner;
    private final BlobStore blobStore;

    @Inject
    public CassandraUserDataTieringService(CqlSession session,
                                           MailboxManager mailboxManager,
                                           CassandraMessageIdDAO cassandraMessageIdDAO,
                                           CassandraMessageDAOV3 cassandraMessageDAOV3,
                                           CassandraAttachmentDAOV2 cassandraAttachmentDAOV2,
                                           CassandraThreadDAO cassandraThreadDAO,
                                           MessageFastViewProjection messageFastViewProjection,
                                           BlobStoreCacheCleaner blobStoreCacheCleaner,
                                           @Named(MetricableBlobStore.BLOB_STORE_IMPLEMENTATION) BlobStore blobStore) {
        this.executor = new CassandraAsyncExecutor(session);
        this.mailboxManager = mailboxManager;
        this.cassandraMessageIdDAO = cassandraMessageIdDAO;
        this.cassandraMessageDAOV3 = cassandraMessageDAOV3;
        this.cassandraAttachmentDAOV2 = cassandraAttachmentDAOV2;
        this.cassandraThreadDAO = cassandraThreadDAO;
        this.messageFastViewProjection = messageFastViewProjection;
        this.blobStoreCacheCleaner = blobStoreCacheCleaner;
        this.blobStore = blobStore;

        this.deleteEmailChanges = session.prepare(deleteFrom(EMAIL_CHANGE_TABLE)
            .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
            .build());

        this.deleteMailboxChanges = session.prepare(deleteFrom(MAILBOX_CHANGE_TABLE)
            .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
            .build());
    }

    @Override
    public Mono<Void> tierUserData(Username username, Duration tiering, UserDataTieringContext context) {
        Date tieringDate = Date.from(Instant.now().minus(tiering));
        AccountId accountId = AccountId.fromUsername(username);
        MailboxSession session = mailboxManager.createSystemSession(username);

        return Mono.when(
            clearChanges(accountId),
            clearOldMessageProjections(username, tieringDate, session, context));
    }

    private Mono<Void> clearChanges(AccountId accountId) {
        return executor.executeVoid(deleteEmailChanges.bind()
                .set(ACCOUNT_ID, accountId.getIdentifier(), TypeCodecs.TEXT))
            .then(executor.executeVoid(deleteMailboxChanges.bind()
                .set(ACCOUNT_ID, accountId.getIdentifier(), TypeCodecs.TEXT)));
    }

    private Mono<Void> clearOldMessageProjections(Username username, Date tieringDate, MailboxSession session, UserDataTieringContext context) {
        return mailboxManager.search(MailboxQuery.privateMailboxesBuilder(session).build(), session)
            .map(metaData -> (CassandraId) metaData.getId())
            .flatMap(mailboxId -> cassandraMessageIdDAO.retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()), LOW_CONCURRENCY)
            .filter(metadata -> metadata.getInternalDate()
                .map(d -> d.before(tieringDate))
                .orElse(false))
            .flatMap(metadata -> applyTiering(username, metadata, context), LOW_CONCURRENCY)
            .then();
    }

    private Mono<Void> applyTiering(Username username, CassandraMessageMetadata metadata, UserDataTieringContext context) {
        CassandraMessageId messageId = (CassandraMessageId) metadata.getComposedMessageId().getComposedMessageId().getMessageId();

        Mono<Void> clearFastView = Mono.from(messageFastViewProjection.delete(messageId));

        Mono<Void> clearThreadAndHeaderCache = metadata.getHeaderContent()
            .<Mono<Void>>map(blobId -> Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), blobId))
                .flatMap(headerBytes -> clearThreadEntries(username, headerBytes))
                .then(Mono.<Void>from(blobStoreCacheCleaner.removeFromCache(blobId))))
            .orElse(Mono.empty());

        Mono<Void> clearAttachments = cassandraMessageDAOV3.retrieveMessage(messageId, FetchType.METADATA)
            .flatMapMany(representation -> Flux.fromIterable(representation.getAttachments()))
            .flatMap(attachment -> cassandraAttachmentDAOV2.delete(attachment.getAttachmentId()))
            .then();

        return Mono.when(clearFastView, clearThreadAndHeaderCache, clearAttachments)
            .doOnSuccess(__ -> context.incrementTiered())
            .onErrorResume(e -> {
                LOGGER.warn("Failed to apply tiering for message {}", messageId, e);
                context.incrementFailed();
                return Mono.empty();
            });
    }

    private Mono<Void> clearThreadEntries(Username username, byte[] headerBytes) {
        Set<Integer> hashMimeMessageIds = extractHashedMimeMessageIds(headerBytes);
        if (hashMimeMessageIds.isEmpty()) {
            return Mono.empty();
        }
        return cassandraThreadDAO.deleteSome(username, hashMimeMessageIds).then();
    }

    private Set<Integer> extractHashedMimeMessageIds(byte[] headerBytes) {
        try {
            DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
            messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
            messageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
            Message message = messageBuilder.parseMessage(new ByteArrayInputStream(headerBytes));

            Optional<MimeMessageId> mimeMessageId = MimeMessageHeadersUtil.parseMimeMessageId(message.getHeader());
            Optional<MimeMessageId> inReplyTo = MimeMessageHeadersUtil.parseInReplyTo(message.getHeader());
            Optional<List<MimeMessageId>> references = MimeMessageHeadersUtil.parseReferences(message.getHeader());

            Set<MimeMessageId> allMimeMessageIds = new HashSet<>();
            mimeMessageId.ifPresent(allMimeMessageIds::add);
            inReplyTo.ifPresent(allMimeMessageIds::add);
            references.ifPresent(allMimeMessageIds::addAll);

            return allMimeMessageIds.stream()
                .map(id -> Hashing.murmur3_32_fixed().hashBytes(id.getValue().getBytes()).asInt())
                .collect(Collectors.toSet());
        } catch (IOException e) {
            LOGGER.warn("Failed to parse message headers for thread cleanup", e);
            return Set.of();
        }
    }
}
