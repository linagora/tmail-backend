package com.linagora.tmail.mailet;

import java.util.Collection;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.mailetcontainer.api.LocalResources;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Mono;

public class TmailLocalResources implements LocalResources {
    private final LocalResources localResources;
    private final TeamMailboxRepository teamMailboxRepository;

    public TmailLocalResources(LocalResources localResources, TeamMailboxRepository teamMailboxRepository) {
        this.localResources = localResources;
        this.teamMailboxRepository = teamMailboxRepository;
    }

    @Override
    public boolean isLocalServer(Domain domain) {
        return localResources.isLocalServer(domain);
    }

    @Override
    public boolean isLocalUser(String name) {
        return localResources.isLocalUser(name);
    }

    @Override
    public boolean isLocalEmail(MailAddress mailAddress) {
        if (localResources.isLocalEmail(mailAddress)) {
            return true;
        }
        if (mailAddress == null) {
            return false;
        }
        return TeamMailbox.asTeamMailbox(mailAddress)
            .map(tm -> Mono.from(teamMailboxRepository.exists(tm)).block())
            .getOrElse(() -> false);
    }

    @Override
    public Collection<MailAddress> localEmails(Collection<MailAddress> mailAddresses) {
        return localResources.localEmails(mailAddresses);
    }
}
