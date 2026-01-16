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

package com.linagora.tmail.vault.dto;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTO;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.apache.james.vault.metadata.StorageInformation;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class TmailDeletedMessageWithStorageInformationConverter {
    private final MessageId.Factory messageIdFactory;
    private final MailboxId.Factory mailboxIdFactory;

    @Inject
    public TmailDeletedMessageWithStorageInformationConverter(MessageId.Factory messageIdFactory,
                                                              MailboxId.Factory mailboxIdFactory) {
        this.messageIdFactory = messageIdFactory;
        this.mailboxIdFactory = mailboxIdFactory;
    }

    public StorageInformation toDomainObject(DeletedMessageWithStorageInformationDTO.StorageInformationDTO storageInformationDTO) {
        return StorageInformation.builder()
            .bucketName(BucketName.of(storageInformationDTO.getBucketName()))
            .blobId(new PlainBlobId(storageInformationDTO.getBlobId()));
    }

    public DeletedMessage toDomainObject(DeletedMessageWithStorageInformationDTO.DeletedMessageDTO deletedMessageDTO) throws AddressException {
        return DeletedMessage.builder()
            .messageId(messageIdFactory.fromString(deletedMessageDTO.getMessageId()))
            .originMailboxes(deserializeOriginMailboxes(deletedMessageDTO.getOriginMailboxes()))
            .user(Username.of(deletedMessageDTO.getOwner()))
            .deliveryDate(ZonedDateTime.parse(deletedMessageDTO.getDeliveryDate()))
            .deletionDate(ZonedDateTime.parse(deletedMessageDTO.getDeletionDate()))
            .sender(MaybeSender.getMailSender(deletedMessageDTO.getSender()))
            .recipients(deserializeRecipients(deletedMessageDTO.getRecipients()))
            .hasAttachment(deletedMessageDTO.getHasAttachment())
            .size(deletedMessageDTO.getSize())
            .subject(deletedMessageDTO.getSubject())
            .build();
    }

    public DeletedMessageWithStorageInformation toDomainObject(DeletedMessageWithStorageInformationDTO deletedMessageWithStorageInfoDTO) throws AddressException {
        return new DeletedMessageWithStorageInformation(
            toDomainObject(deletedMessageWithStorageInfoDTO.getDeletedMessage()),
            toDomainObject(deletedMessageWithStorageInfoDTO.getStorageInformation()));
    }

    private ImmutableList<MailboxId> deserializeOriginMailboxes(List<String> originMailboxes) {
        return originMailboxes.stream()
            .map(mailboxId -> mailboxIdFactory.fromString(mailboxId))
            .collect(ImmutableList.toImmutableList());
    }

    private ImmutableList<MailAddress> deserializeRecipients(List<String> recipients) throws AddressException {
        return recipients.stream()
            .map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow())
            .collect(ImmutableList.toImmutableList());
    }
}
