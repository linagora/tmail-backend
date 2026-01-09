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

package com.linagora.tmail.encrypted.postgres;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.reactivestreams.Publisher;

import com.linagora.tmail.encrypted.AttachmentNotFoundException;
import com.linagora.tmail.encrypted.EncryptedEmailContent;
import com.linagora.tmail.encrypted.EncryptedEmailContentStore;
import com.linagora.tmail.encrypted.EncryptedEmailDetailedView;
import com.linagora.tmail.encrypted.EncryptedEmailFastView;
import com.linagora.tmail.encrypted.MessageNotFoundException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;
import scala.runtime.BoxedUnit;

public class PostgresEncryptedEmailContentStore implements EncryptedEmailContentStore {
    private final PostgresEncryptedEmailStoreDAO encryptedEmailStoreDAO;
    private final BlobStore blobStore;

    @Inject
    public PostgresEncryptedEmailContentStore(PostgresEncryptedEmailStoreDAO encryptedEmailStoreDAO,
                                              BlobStore blobStore) {
        this.encryptedEmailStoreDAO = encryptedEmailStoreDAO;
        this.blobStore = blobStore;
    }

    @Override
    public Publisher<BoxedUnit> store(MessageId messageId, EncryptedEmailContent encryptedEmailContent) {
        return Flux.fromIterable(CollectionConverters.asJavaCollection(encryptedEmailContent.encryptedAttachmentContents()))
            .concatMap(encryptedAttachmentContent ->
                Mono.from(blobStore.save(BucketName.DEFAULT, encryptedAttachmentContent, BlobStore.StoragePolicy.LOW_COST)))
            .index()
            .collectMap(indexBlobIdPair -> indexBlobIdPair.getT1().intValue() + EncryptedEmailContentStore.POSITION_NUMBER_START_AT(),
                indexBlobIdPair -> indexBlobIdPair.getT2())
            .flatMap(positionBlobIdMapping -> encryptedEmailStoreDAO.insert(PostgresMessageId.class.cast(messageId),
                encryptedEmailContent,
                positionBlobIdMapping))
            .thenReturn(BoxedUnit.UNIT);
    }

    @Override
    public Publisher<BoxedUnit> delete(MessageId messageId) {
        PostgresMessageId postgresMessageId = PostgresMessageId.class.cast(messageId);
        return deleteBlobStore(postgresMessageId)
            .then(encryptedEmailStoreDAO.delete(postgresMessageId))
            .thenReturn(BoxedUnit.UNIT);
    }

    @Override
    public Publisher<EncryptedEmailFastView> retrieveFastView(MessageId messageId) {
        return encryptedEmailStoreDAO.getEmail(PostgresMessageId.class.cast(messageId))
            .map(encryptedEmailDetailedView -> EncryptedEmailFastView.from(messageId, encryptedEmailDetailedView))
            .switchIfEmpty(Mono.error(new MessageNotFoundException(messageId)));
    }

    @Override
    public Publisher<EncryptedEmailDetailedView> retrieveDetailedView(MessageId messageId) {
        return encryptedEmailStoreDAO.getEmail(PostgresMessageId.class.cast(messageId))
            .switchIfEmpty(Mono.error(new MessageNotFoundException(messageId)));
    }

    @Override
    public Publisher<BlobId> retrieveAttachmentContent(MessageId messageId, int position) {
        return encryptedEmailStoreDAO.getBlobId(PostgresMessageId.class.cast(messageId), position)
            .switchIfEmpty(Mono.error(new AttachmentNotFoundException(messageId, position)));
    }

    private Mono<Void> deleteBlobStore(PostgresMessageId messageId) {
        return encryptedEmailStoreDAO.getBlobIds(messageId)
            .flatMap(blobId -> blobStore.delete(BucketName.DEFAULT, blobId))
            .then();
    }
}
