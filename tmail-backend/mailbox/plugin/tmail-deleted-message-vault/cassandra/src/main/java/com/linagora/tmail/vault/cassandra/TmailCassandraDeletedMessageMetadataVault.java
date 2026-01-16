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

package com.linagora.tmail.vault.cassandra;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.metadata.DeletedMessageIdentifier;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.apache.james.vault.metadata.StorageInformation;
import org.reactivestreams.Publisher;

public class TmailCassandraDeletedMessageMetadataVault implements DeletedMessageMetadataVault {
    private final TmailMetadataDAO metadataDAO;
    private final TmailStorageInformationDAO storageInformationDAO;
    private final TmailUserPerBucketDAO userPerBucketDAO;

    @Inject
    public TmailCassandraDeletedMessageMetadataVault(TmailMetadataDAO metadataDAO,
                                                     TmailStorageInformationDAO storageInformationDAO,
                                                     TmailUserPerBucketDAO userPerBucketDAO) {
        this.metadataDAO = metadataDAO;
        this.storageInformationDAO = storageInformationDAO;
        this.userPerBucketDAO = userPerBucketDAO;
    }

    @Override
    public Publisher<Void> store(DeletedMessageWithStorageInformation deletedMessage) {
        BucketName bucketName = deletedMessage.getStorageInformation().getBucketName();
        Username owner = deletedMessage.getDeletedMessage().getOwner();
        MessageId messageId = deletedMessage.getDeletedMessage().getMessageId();
        return storageInformationDAO.referenceStorageInformation(owner, messageId, deletedMessage.getStorageInformation())
            .then(metadataDAO.store(deletedMessage))
            .then(userPerBucketDAO.addUser(bucketName, owner));
    }

    @Override
    public Publisher<Void> removeMetadataRelatedToBucket(BucketName bucketName) {
        return userPerBucketDAO.retrieveUsers(bucketName)
            .concatMap(user -> metadataDAO.retrieveMessageIds(bucketName, user)
                .map(messageId -> new DeletedMessageIdentifier(user, messageId))
                .concatMap(deletedMessageIdentifier -> storageInformationDAO.deleteStorageInformation(
                    deletedMessageIdentifier.getOwner(),
                    deletedMessageIdentifier.getMessageId()))
                .then(metadataDAO.deleteInBucket(bucketName, user)))
            .then(userPerBucketDAO.deleteBucket(bucketName));
    }

    @Override
    public Publisher<Void> remove(BucketName bucketName, Username username, MessageId messageId) {
        return storageInformationDAO.deleteStorageInformation(username, messageId)
            .then(metadataDAO.deleteMessage(bucketName, username, messageId));
    }

    @Override
    public Publisher<StorageInformation> retrieveStorageInformation(Username username, MessageId messageId) {
        return storageInformationDAO.retrieveStorageInformation(username, messageId);
    }

    @Override
    public Publisher<DeletedMessageWithStorageInformation> listMessages(BucketName bucketName, Username username) {
        return metadataDAO.retrieveMetadata(bucketName, username);
    }

    @Override
    public Publisher<BucketName> listRelatedBuckets() {
        return userPerBucketDAO.retrieveBuckets();
    }
}
