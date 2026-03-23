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

package com.linagora.tmail.james.jmap.projections;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.util.ReactorUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MailboxReadRightsResolver {
    private final MailboxACLResolver mailboxACLResolver;

    @Inject
    public MailboxReadRightsResolver(MailboxACLResolver mailboxACLResolver) {
        this.mailboxACLResolver = mailboxACLResolver;
    }

    public Flux<Username> usersHavingReadRight(Username owner, MessageManager mailbox, MailboxSession ownerSession) {
        return Mono.fromCallable(() -> mailbox.getResolvedAcl(ownerSession))
            .flatMapMany(acl -> usersHavingReadRight(owner, acl));
    }

    public Flux<Username> usersHavingReadRight(Username owner, MailboxACL acl) {
        return Flux.concat(
                Flux.just(owner),
                Flux.fromIterable(acl.getEntries().keySet())
                    .filter(entryKey -> MailboxACL.NameType.user.equals(entryKey.getNameType()))
                    .map(MailboxACL.EntryKey::getName)
                    .map(Username::of))
            .filterWhen(username -> hasReadRight(owner, acl, username))
            .distinct();
    }

    public Mono<Boolean> hasReadRight(Username owner, MailboxACL acl, Username username) {
        return Mono.fromCallable(() -> mailboxACLResolver.resolveRights(username, acl, owner).contains(MailboxACL.Right.Read))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }
}
