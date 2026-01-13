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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.vault.blob;

import static com.linagora.tmail.vault.blob.TmailBlobStoreDeletedMessageVault.APPEND_METRIC_NAME;
import static com.linagora.tmail.vault.blob.TmailBlobStoreDeletedMessageVault.DELETE_METRIC_NAME;
import static com.linagora.tmail.vault.blob.TmailBlobStoreDeletedMessageVault.LOAD_MIME_MESSAGE_METRIC_NAME;
import static com.linagora.tmail.vault.blob.TmailBlobStoreDeletedMessageVault.SEARCH_METRIC_NAME;
import static org.apache.james.vault.DeletedMessageFixture.CONTENT;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_GENERATOR;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_WITH_SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.MESSAGE_ID;
import static org.apache.james.vault.DeletedMessageFixture.NOW;
import static org.apache.james.vault.DeletedMessageFixture.SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.USERNAME;
import static org.apache.james.vault.search.Query.ALL;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVaultContract;
import org.apache.james.vault.DeletedMessageVaultSearchContract;
import org.apache.james.vault.memory.metadata.MemoryDeletedMessageMetadataVault;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class TmailBlobStoreDeletedMessageVaultTest implements DeletedMessageVaultContract, DeletedMessageVaultSearchContract.AllContracts {
    private TmailBlobStoreDeletedMessageVault messageVault;
    private UpdatableTickingClock clock;
    private RecordingMetricFactory metricFactory;

    @BeforeEach
    void setUp() throws Exception {
        clock = new UpdatableTickingClock(NOW.toInstant());
        metricFactory = new RecordingMetricFactory();
        MemoryBlobStoreDAO blobStoreDAO = new MemoryBlobStoreDAO();
        BlobId.Factory blobIdFactory = new PlainBlobId.Factory();

        messageVault = new TmailBlobStoreDeletedMessageVault(metricFactory, new MemoryDeletedMessageMetadataVault(),
            BlobStoreFactory.builder()
                .blobStoreDAO(blobStoreDAO)
                .blobIdFactory(blobIdFactory)
                .defaultBucketName()
                .passthrough(),
            blobStoreDAO, new BucketNameGenerator(clock), new BlobIdTimeGenerator(blobIdFactory, clock));
    }

    @Override
    public TmailBlobStoreDeletedMessageVault getVault() {
        return messageVault;
    }

    @Override
    public UpdatableTickingClock getClock() {
        return clock;
    }

    @Test
    void appendShouldPublishAppendTimerMetrics() {
        Mono.from(messageVault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)))
            .block();
        Mono.from(messageVault.append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)))
            .block();

        assertThat(metricFactory.executionTimesFor(APPEND_METRIC_NAME))
            .hasSize(2);
    }

    @Test
    void searchShouldPublishSearchTimerMetrics() {
        Mono.from(messageVault.search(USERNAME, ALL))
            .block();
        Mono.from(messageVault.search(USERNAME, ALL))
            .block();

        assertThat(metricFactory.executionTimesFor(SEARCH_METRIC_NAME))
            .hasSize(2);
    }

    @Test
    void loadMimeMessageShouldPublishLoadMimeMessageTimerMetrics() {
        Mono.from(messageVault.loadMimeMessage(USERNAME, MESSAGE_ID))
            .block();
        Mono.from(messageVault.loadMimeMessage(USERNAME, MESSAGE_ID))
            .block();

        assertThat(metricFactory.executionTimesFor(LOAD_MIME_MESSAGE_METRIC_NAME))
            .hasSize(2);
    }

    @Test
    void deleteShouldPublishDeleteTimerMetrics() {
        Mono.from(messageVault.delete(USERNAME, MESSAGE_ID))
            .block();
        Mono.from(messageVault.delete(USERNAME, MESSAGE_ID))
            .block();

        assertThat(metricFactory.executionTimesFor(DELETE_METRIC_NAME))
            .hasSize(2);
    }

    @Test
    public void loadMimeMessageShouldReturnOldMessage() {
        Mono.from(getVault().appendV1(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Mono.from(getVault().loadMimeMessage(USERNAME, MESSAGE_ID)).blockOptional())
            .isNotEmpty()
            .satisfies(maybeContent -> assertThat(maybeContent.get()).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
    }

    @Test
    public void loadMimeMessageShouldReturnEmptyWhenOldMessageDeleted() {
        Mono.from(getVault().appendV1(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        Mono.from(getVault().delete(USERNAME, MESSAGE_ID)).block();

        assertThat(Mono.from(getVault().loadMimeMessage(USERNAME, MESSAGE_ID)).blockOptional())
            .isEmpty();
    }

    @Test
    public void searchAllShouldReturnOldMessage() {
        Mono.from(getVault().appendV1(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .containsOnly(DELETED_MESSAGE);
    }

    @Test
    public void searchAllShouldReturnOldAndNewMessages() {
        Mono.from(getVault().appendV1(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(getVault().append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .containsOnly(DELETED_MESSAGE, DELETED_MESSAGE_2);
    }

    @Test
    public void searchAllShouldSupportLimitQueryWithOldAndNewMessages() {
        Mono.from(getVault().appendV1(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(getVault().appendV1(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();
        DeletedMessage deletedMessage3 = DELETED_MESSAGE_GENERATOR.apply(InMemoryMessageId.of(33).getRawId());
        Mono.from(getVault().append(deletedMessage3, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Flux.from(getVault().search(USERNAME, Query.of(1, List.of()))).collectList().block())
            .hasSize(1);
        assertThat(Flux.from(getVault().search(USERNAME, Query.of(3, List.of()))).collectList().block())
            .containsExactlyInAnyOrder(DELETED_MESSAGE, DELETED_MESSAGE_2, deletedMessage3);
        assertThat(Flux.from(getVault().search(USERNAME, Query.of(4, List.of()))).collectList().block())
            .containsExactlyInAnyOrder(DELETED_MESSAGE, DELETED_MESSAGE_2, deletedMessage3);
    }

    @Test
    public void searchShouldReturnMatchingOldMessages() {
        Mono.from(getVault().appendV1(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(getVault().appendV1(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT))).block();

        assertThat(
            Flux.from(getVault().search(USERNAME,
                    Query.of(CriterionFactory.subject().containsIgnoreCase(SUBJECT))))
                .collectList().block())
            .containsOnly(DELETED_MESSAGE_WITH_SUBJECT);
    }

    @Override
    @Disabled
    public void deleteExpiredMessagesTaskShouldCompleteWhenNoMail() throws InterruptedException {

    }

    @Override
    @Disabled
    public void deleteExpiredMessagesTaskShouldCompleteWhenAllMailsDeleted() throws InterruptedException {

    }

    @Override
    @Disabled
    public void deleteExpiredMessagesTaskShouldCompleteWhenOnlyRecentMails() throws InterruptedException {

    }

    @Override
    @Disabled
    public void deleteExpiredMessagesTaskShouldCompleteWhenOnlyOldMails() throws InterruptedException {

    }

    @Override
    @Disabled
    public void deleteExpiredMessagesTaskShouldDoNothingWhenEmpty() throws InterruptedException {

    }

    @Override
    @Disabled
    public void deleteExpiredMessagesTaskShouldNotDeleteRecentMails() throws InterruptedException {

    }

    @Override
    @Disabled
    public void deleteExpiredMessagesTaskShouldDeleteOldMails() throws InterruptedException {

    }

    @Override
    @Disabled
    public void deleteExpiredMessagesTaskShouldDeleteOldMailsWhenRunSeveralTime() throws InterruptedException {

    }
}