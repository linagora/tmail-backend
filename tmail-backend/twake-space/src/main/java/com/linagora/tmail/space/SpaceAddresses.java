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

package com.linagora.tmail.space;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;

import com.linagora.tmail.team.TeamMailbox;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import scala.jdk.javaapi.OptionConverters;

/** The addresses a new space may take, from its name: see {@link SpaceNames}. */
public class SpaceAddresses {
    @FunctionalInterface
    public interface MailingLists {
        MailingLists NONE = address -> false;

        boolean exists(MailAddress address);
    }

    private final SpaceConfiguration configuration;
    private final MailingLists mailingLists;

    @Inject
    public SpaceAddresses(SpaceConfiguration configuration, MailingLists mailingLists) {
        this.configuration = configuration;
        this.mailingLists = mailingLists;
    }

    public List<TeamMailbox> candidates(Domain domain, String spaceName) {
        return SpaceNames.candidates(SpaceNames.mailboxName(spaceName, configuration.teamMailboxPrefix()))
            .stream()
            .flatMap(name -> teamMailbox(domain, name).stream())
            .toList();
    }

    /** TMail creates a team mailbox over an LDAP mailing list's address, which then gets its mail: skip those. */
    public Mono<Boolean> isMailingList(TeamMailbox teamMailbox) {
        return Mono.fromCallable(() -> mailingLists.exists(teamMailbox.asMailAddress()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Optional<TeamMailbox> teamMailbox(Domain domain, String name) {
        return OptionConverters.toJava(TeamMailbox.fromJava(domain, name));
    }
}
