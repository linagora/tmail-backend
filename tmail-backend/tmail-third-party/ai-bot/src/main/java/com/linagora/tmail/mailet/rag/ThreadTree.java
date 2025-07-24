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
package com.linagora.tmail.mailet.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.utils.MimeMessageHeadersUtil;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

/**
 * A <b>ThreadTree</b> is a collection of messages in a thread, structured as a tree.
 * 
 * <p> A ThreadTree stores the <code>Thread-ID</code>, <code>Message-ID</code> and <code>In-Reply-To</code>
 * email headers of its messages. These relationships are used to represent the skeleton of a thread,
 * without storing full headers.
 * </p>
 * <p> A ThreadTree serves as a permanent record of the relationships between emails, even when some of them
 * are deleted from the mailbox or even from the server.
 * </p>
 * <p> Messages that are added to the data structure are referred as nodes. Children nodes of a parent
 * are ordered by added date. The whole tree can be partitioned into branches, i.e. successions of
 * eldest nodes.
 * </p>
 * <p>
 * Nodes are never dropped from the data structure, only marked as deleted. That means a branch is stable,
 * meaning that a node remains in the same branch.
 * </p>
 * <p> When some messages in the thread are marked deleted, the tree becomes a forest.
 * See https://en.wikipedia.org/wiki/Tree_(graph_theory)#Forest
 * </p>
*/
// TODO: add tests
class ThreadTree {
    private ThreadId threadId;
    private ArrayList<MimeMessageId> nodes;
    private HashMap<MimeMessageId, MimeMessageId> nodeParent;
    private HashMap<MimeMessageId, ArrayList<MimeMessageId>> nodeChildren;
    private HashSet<MimeMessageId> markedDeleted;

    private ThreadTree(ThreadId threadId) {
        this.threadId = threadId;
        this.nodes = new ArrayList<>();
        this.nodeParent = new HashMap<>();
        this.nodeChildren = new HashMap<>();
        this.markedDeleted = new HashSet<>();
    }

    public ThreadTree(ThreadId threadId, List<MessageResult> messages) throws MailboxException {
        this(threadId);
        addMessages(messages);
    }

    /**
     * Assumption: messages and message/reply pairs are ordered by add date.
     */
    public ThreadTree(ThreadId threadId, List<MimeMessageId> messages, List<Pair<MimeMessageId, MimeMessageId>> messageReplyPairs) {
        this(threadId);
        for (MimeMessageId message : messages) {
            add(message);
        }
        for (var pair : messageReplyPairs) {
            MimeMessageId message = pair.getLeft();
            MimeMessageId reply = pair.getRight();
            link(message, reply);
        }
    }

    private void addMessages(List<MessageResult> messages) throws MailboxException {
        List<MessageResult> sortedMessages = new ArrayList<>(messages);
        // Sorting the messages ensures that children are added in chronological order.
        sortedMessages.sort(Comparator.comparing(MessageResult::getInternalDate));

        for (MessageResult message : sortedMessages) {
            Headers headers = message.getHeaders();
            MimeMessageId node = MessageIdMapper.parseMimeMessageId(headers).get();
            Optional<MimeMessageId> parentOpt = MessageIdMapper.parseInReplyTo(headers);
            boolean isNewMessage = add(node);
            assert isNewMessage;
            parentOpt.ifPresent(parent -> {
                // Should be true because the parent appears before the child in the sorted message list
                if (contains(parent)) {
                    link(parent, node);
                }
                // Otherwise, the parent is likely a deleted message
            });
        }
    }

    public ThreadId getThreadId() {
        return threadId;
    }

    /**
     * Returns the list of nodes, including deleted ones.
     * 
     * <p>This function is used for storage purposes.</p>
    */
    private List<MimeMessageId> getNodes() {
        return nodes;
    }

    /**
     * Returns the list of message/reply pairs, including deleted messages.
     * 
     * <p>In the returned list, the order of the replies of a same message is guaranteed to be consistent
     * with the actual internal dates of the messages.</p>
     * <p>This function is used for storage purposes.</p>
    */
    private List<Pair<MimeMessageId, MimeMessageId>> getMessageReplyPairs() {
        List<Pair<MimeMessageId, MimeMessageId>> pairs = new ArrayList<>();
        for (MimeMessageId node : nodes) {
            for (MimeMessageId child : nodeChildren.get(node)) {
                pairs.add(Pair.of(node, child));
            }
        }
        return pairs;
    }

    /**
     * Returns the list of nodes that are marked deleted.
     * 
     * <p>This function is used for storage purposes.</p>
    */
    private Set<MimeMessageId> getMarkedDeleted() {
        return markedDeleted;
    }

    /**
     * Save the data structure to storage.
     */
    public Mono<Void> saveToStorage() {
        // TODO: In a table or in a file, or in metadata of a dummy file on OpenRAG
        // (but in the last case, it's the responsibility of RagListener)
        // Depending on the storage backend, this will determine the serialization format.
        return Mono.empty();
    }

    /**
     * Load the data structure from storage.
     * 
     * @return a ThreadTree if it is present, or an empty Mono if it is absent.
     */
    public static Mono<ThreadTree> loadFromStorage(ThreadId threadId) {
        // TODO: deserialize or load from table.
        // TODO: must emit an error if state is inconsistent with OpenRAG
        return Mono.empty();
    }

    /**
     * Returns whether a message is stored as a node in the data structure.
     * 
     * <p>A node that's been marked deleted is still contained in the data structure forever.
     * </p>
    */
    private boolean contains(MimeMessageId message) {
        return nodeChildren.containsKey(message);
    }

    /**
     * Returns whether a message is marked as deleted from its mailbox.
     */
    public boolean isMarkedDeleted(MimeMessageId message) {
        // TODO: possibly use MailboxMessage.isDeleted()? what does that mean?
        return markedDeleted.contains(message);
    }

    /**
     * Returns whether an email is a reply, possibly to a deleted parent.
    */
    private boolean parentExists(MimeMessageId node) {
        return nodeParent.containsKey(node);
    }

    /**
     * The parent of an email, possibly a deleted node.
     * 
     * @return the parent id, or <code>null</code> if its does not exist
     */
    private MimeMessageId parentOf(MimeMessageId node) {
        return nodeParent.get(node);
    }

    /**
     * The list of replies to an email, in chronological order, including deleted nodes.
     */
    private ListIterator<MimeMessageId> iterateChildren(MimeMessageId parent) {
        return nodeChildren.get(parent).listIterator();
    }

    /**
     * The number of replies to an email, including deleted nodes.
     */
    private int childrenCount(MimeMessageId node) {
        return nodeChildren.get(node).size();
    }

    /**
     * Add the newest message to the forest as an isolated node.
     * 
     * @return whether the message was already in the structure.
     */
    public boolean add(MimeMessageId message) {
        boolean isNew = !contains(message);
        if (isNew) {
            nodes.add(message);
            nodeChildren.put(message, new ArrayList<>());
        }
        return isNew;
    }

    /**
     * Mark a node as a parent of another.
     * 
     * @param parent the parent message
     * @param child the reply message
     * @return whether the parent-child relationship was already established.
     */
    private boolean link(MimeMessageId parent, MimeMessageId child) {
        nodeParent.put(child, parent);
        return nodeChildren.get(parent).add(child);
    }

    /**
     * Add a node, and mark it as a child of a parent if the parent exists and is not deleted.
     * 
     * @param parent the parent message
     * @param child the reply message
     * @return whether the parent is not deleted.
     */
    public boolean addAndTryLink(MimeMessageId child, MimeMessageId parent) {
        boolean isPresent = add(child);
        assert !isPresent;
        boolean canLink = contains(parent) && !isMarkedDeleted(parent);
        if (canLink) {
            link(child, parent);
        }
        return canLink;
    }

    /**
     * Returns whether a node is the first of a branch.
     * 
     * <p>The first node of a branch is, by definition, a root node or a non-leftmost node.</p>
     */
    private boolean isBranchStart(MimeMessageId node) {
        return isRoot(node) || isLeftmostChild(node);
    }

    private boolean isRoot(MimeMessageId node) {
        return !nodeParent.containsKey(node);
    }

    private boolean isLeftmostChild(MimeMessageId node) {
        return nodeChildren.get(parentOf(node)).get(0).equals(node);
    }

    private boolean isLeaf(MimeMessageId node) {
        return nodeChildren.get(node).isEmpty();
    }

    private boolean isBranchEnd(MimeMessageId node) {
        return isLeaf(node);
    }

    /**
     * Find the first element of the branch of a node.
     */
    private MimeMessageId findBranchStart(MimeMessageId node) {
        while (!isBranchStart(node)) {
            node = parentOf(node);
        }
        return node;
    }

    /**
     * The list of nodes in the branch, from top to bottom, including deleted nodes.
    */
    public List<MimeMessageId> getBranchOf(MimeMessageId node) {
        MimeMessageId start = findBranchStart(node);
        List<MimeMessageId> branch = new ArrayList<>();
        branch.add(start);
        node = start;
        while (!isBranchEnd(node)) {
            node = nodeChildren.get(node).get(0);
            branch.add(node);
        }
        return branch;
    }

    /**
     * Mark a message as deleted in the mailbox.
     * 
     * <p> A message should be marked as deleted when is not in its original mailbox.
     * It could be moved to Trash or deleted permanently.
     * </p>
     * <p> The headers of a permanently deleted message cannot be retrieved from the server. However,
     * the message content could be present in the quoted message history of a descendant email.
     * </p>
     */
    public boolean markDeleted(MimeMessageId message) {
        return markedDeleted.add(message);
    }

    /**
     * Returns whether the whole branch containing a node is marked deleted.
     * 
     * <p>If a branch is fully marked deleted, the document should be deleted from the RAG database.
     * However, the nodes in the branch are never dropped from the data structure.
     * </p>
     */
    public boolean isBranchDeleted(MimeMessageId node) {
        return getBranchOf(node).stream()
            .map(message -> isMarkedDeleted(message))
            .allMatch(b -> b);

    }
}