package com.linagora.tmail.imap;

import java.util.Collection;

import org.apache.james.imap.message.response.NamespaceResponse;
import org.apache.james.imap.message.response.NamespaceResponse.Namespace;
import org.apache.james.imap.processor.NamespaceSupplier;
import org.apache.james.mailbox.MailboxSession;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.team.TeamMailboxNameSpace;

public class TMailNamespaceSupplier implements NamespaceSupplier {

    @Override
    public Collection<Namespace> personalNamespaces(MailboxSession session) {
        return ImmutableList.of(new NamespaceResponse.Namespace("", session.getPathDelimiter()));
    }

    @Override
    public Collection<Namespace> otherUsersNamespaces(MailboxSession session) {
        return ImmutableList.of(new NamespaceResponse.Namespace("#user", session.getPathDelimiter()));
    }

    @Override
    public Collection<Namespace> sharedNamespaces(MailboxSession session) {
        return ImmutableList.of(new NamespaceResponse.Namespace(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE(), session.getPathDelimiter()));
    }
}
