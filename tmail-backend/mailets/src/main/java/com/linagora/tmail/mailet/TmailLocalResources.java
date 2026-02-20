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

package com.linagora.tmail.mailet;

import java.util.Collection;
import java.util.List;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.mailetcontainer.api.LocalResources;
import org.apache.james.user.api.UsersRepository;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

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
        MailAddress strippedAddress = mailAddress.stripDetails(UsersRepository.LOCALPART_DETAIL_DELIMITER);
        return TeamMailbox.asTeamMailbox(strippedAddress)
            .map(tm -> Mono.from(teamMailboxRepository.exists(tm)).block())
            .getOrElse(() -> false);
    }

    @Override
    public Collection<MailAddress> localEmails(Collection<MailAddress> mailAddresses) {
        Collection<MailAddress> localUsersMailAddresses = localResources.localEmails(mailAddresses);
        List<MailAddress> mailAddressesLeft = mailAddresses.stream()
            .filter(m -> !localUsersMailAddresses.contains(m))
            .toList();

        List<MailAddress> teamMailboxesMailAddresses = Flux.fromIterable(mailAddressesLeft)
            .filterWhen(mailAddress -> {
                MailAddress strippedAddress = mailAddress.stripDetails(UsersRepository.LOCALPART_DETAIL_DELIMITER);
                return OptionConverters.toJava(TeamMailbox.asTeamMailbox(strippedAddress))
                    .map(teamMailbox -> Mono.from(teamMailboxRepository.exists(teamMailbox)).map(Boolean.TRUE::equals))
                    .orElse(Mono.just(false));
            })
            .collectList()
            .block();

        return ImmutableList.<MailAddress>builder()
            .addAll(localUsersMailAddresses)
            .addAll(teamMailboxesMailAddresses)
            .build();
    }
}
