/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package com.linagora.tmail.james.app;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.IOException;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.AttachmentIdFactory;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2.DAOAttachment;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMapper;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.model.StringBackedAttachmentId;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;

public class TMailCleverCassandraAttachmentMapper extends CassandraAttachmentMapper {

    private final CassandraAttachmentDAOV2 attachmentDAOV2;
    private final BlobStore blobStore;
    private final AttachmentIdFactory attachmentIdFactory;

    @Inject
    public TMailCleverCassandraAttachmentMapper(CassandraAttachmentDAOV2 attachmentDAOV2, BlobStore blobStore, AttachmentIdFactory attachmentIdFactory) {
        super(attachmentDAOV2, blobStore, attachmentIdFactory);
        this.attachmentDAOV2 = attachmentDAOV2;
        this.blobStore = blobStore;
        this.attachmentIdFactory = attachmentIdFactory;
    }
    @Override
    protected Mono<MessageAttachmentMetadata> storeAttachmentAsync(ParsedAttachment parsedAttachment, MessageId ownerMessageId) {
        try {
            // Only change is computation of the attachment ID, modified to be alligned with the blobId
            AttachmentId attachmentId = computeAttachmentId(parsedAttachment, ownerMessageId);
            ByteSource content = parsedAttachment.getContent();
            long size = content.size();
            return Mono.from(blobStore.save(blobStore.getDefaultBucketName(), content, LOW_COST))
                .map(blobId -> new DAOAttachment(ownerMessageId, attachmentId, blobId, parsedAttachment.getContentType(), size))
                .flatMap(attachmentDAOV2::storeAttachment)
                .thenReturn(parsedAttachment.asMessageAttachment(attachmentId, size, ownerMessageId));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AttachmentId computeAttachmentId(ParsedAttachment parsedAttachment, MessageId ownerMessageId) {
        if (parsedAttachment instanceof TMailCleverParsedAttachment TMailCleverParsedAttachment) {
            return StringBackedAttachmentId.from(TMailCleverParsedAttachment.translate(ownerMessageId));
        }
        return attachmentIdFactory.random();
    }
}
