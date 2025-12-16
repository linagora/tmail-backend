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
 *******************************************************************/

package com.linagora.tmail.mailet.rag;

import jakarta.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.mailet.rag.httpclient.DocumentId;
import com.linagora.tmail.mailet.rag.httpclient.OpenRagHttpClient;
import com.linagora.tmail.mailet.rag.httpclient.Partition;

import reactor.core.publisher.Mono;

public class RagDeletionListener implements EventListener.ReactiveGroupEventListener {
    public static class RagDeletionListenerGroup extends Group {

    }

    private static final Group RAG_DELETION_LISTENER_GROUP = new RagDeletionListenerGroup();
    private static final Logger LOGGER = LoggerFactory.getLogger(RagDeletionListener.class);

    private final Partition.Factory partitionFactory;
    private final OpenRagHttpClient openRagHttpClient;

    @Inject
    public RagDeletionListener(Partition.Factory partitionFactory, OpenRagHttpClient openRagHttpClient) {
        this.partitionFactory = partitionFactory;
        this.openRagHttpClient = openRagHttpClient;
    }

    @Override
    public Group getDefaultGroup() {
        return RAG_DELETION_LISTENER_GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxEvents.MessageContentDeletionEvent;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxEvents.MessageContentDeletionEvent contentDeletionEvent) {
            return deleteRagDocument(contentDeletionEvent);
        }

        return Mono.empty();
    }

    private Mono<Void> deleteRagDocument(MailboxEvents.MessageContentDeletionEvent contentDeletionEvent) {
        Partition partition = partitionFactory.forUsername(contentDeletionEvent.getUsername());
        DocumentId documentId = new DocumentId(contentDeletionEvent.messageId());

        return openRagHttpClient.deleteDocument(partition, documentId)
            .doOnSuccess(any -> LOGGER.debug("Deleted RAG document {} with partition {} for user {}",
                documentId.asString(), partition.partitionName(), contentDeletionEvent.getUsername().asString()))
            .doOnError(error -> LOGGER.error("Failed to delete RAG document {} with partition {} for user {}",
                documentId.asString(), partition.partitionName(), contentDeletionEvent.getUsername().asString(), error));
    }
}
