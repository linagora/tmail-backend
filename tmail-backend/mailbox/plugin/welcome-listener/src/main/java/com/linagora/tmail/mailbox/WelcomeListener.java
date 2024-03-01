package com.linagora.tmail.mailbox;

import java.io.IOException;

import javax.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.io.IOUtils;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.MailboxConstants;
import org.reactivestreams.Publisher;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class WelcomeListener implements EventListener.ReactiveGroupEventListener {
    private static final WelcomeListenerGroup GROUP = new WelcomeListenerGroup();

    public static class WelcomeListenerGroup extends Group {

    }

    private final MailboxManager mailboxManager;
    private final MessageManager.AppendCommand appendCommand;


    @Inject
    public WelcomeListener(MailboxManager mailboxManager,
                           FileSystem fileSystem,
                           HierarchicalConfiguration<ImmutableNode> configuration) {
        this(mailboxManager, fileSystem, configuration.getString("eml"));
    }

    @VisibleForTesting
    WelcomeListener(MailboxManager mailboxManager,
                    FileSystem fileSystem,
                    String emlLocation) {
        this.mailboxManager = mailboxManager;
        this.appendCommand = retrieveAppendCommand(fileSystem, emlLocation);
    }

    private MessageManager.AppendCommand retrieveAppendCommand(FileSystem fileSystem, String emlLocation) {
        try {
            return MessageManager.AppendCommand.builder()
                .build(IOUtils.toByteArray(fileSystem.getResource(emlLocation)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isHandling(Event event) {
        if (event instanceof MailboxEvents.MailboxAdded) {
            MailboxEvents.MailboxAdded mailboxAdded = (MailboxEvents.MailboxAdded) event;
            return mailboxAdded.getMailboxPath().getName().equalsIgnoreCase(MailboxConstants.INBOX)
                && mailboxAdded.getMailboxPath().getUser().equals(event.getUsername());
        }
        return false;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (isHandling(event)) {
            MailboxEvents.MailboxAdded mailboxAdded = (MailboxEvents.MailboxAdded) event;
            MailboxSession session = mailboxManager.createSystemSession(event.getUsername());

            return Mono.from(mailboxManager.getMailboxReactive(mailboxAdded.getMailboxId(), session))
                .flatMap(mailbox -> Mono.from(mailbox.appendMessageReactive(appendCommand, session)))
                .then();
        }
        return Mono.empty();
    }

}
