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

package com.linagora.tmail.smtp;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.Authorizator.AuthorizationState;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.smtpserver.UsersRepositoryAuthHook;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TMailUsersRepositoryAuthHook extends UsersRepositoryAuthHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(TMailUsersRepositoryAuthHook.class);
    private static final String DELEGATION_SPLIT_CHARACTER = "+";

    private final UsersRepository users;
    private final Authorizator authorizator;

    @Inject
    public TMailUsersRepositoryAuthHook(UsersRepository users, Authorizator authorizator) {
        super(users, authorizator);
        this.users = users;
        this.authorizator = authorizator;
    }

    @Override
    public HookResult doAuth(SMTPSession session, Username username, String password) {
        String localPart = username.getLocalPart();
        if (!localPart.contains(DELEGATION_SPLIT_CHARACTER)) {
            return super.doAuth(session, username, password);
        }
        String delegatedUser = StringUtils.substringBefore(localPart, DELEGATION_SPLIT_CHARACTER);
        String ownerUser = StringUtils.substringAfter(localPart, DELEGATION_SPLIT_CHARACTER);

        if (StringUtils.isAnyEmpty(delegatedUser, ownerUser)) {
            return HookResult.DECLINED;
        }
        Username authenticationUser = Username.of(delegatedUser).withDefaultDomain(username.getDomainPart());
        Username delegateUsername = Username.of(ownerUser).withDefaultDomain(username.getDomainPart());

        try {
            return users.test(authenticationUser, password)
                .filter(user -> user.equals(authenticationUser))
                .map(user -> doAuthWithDelegation(session, authenticationUser, delegateUsername))
                .orElse(HookResult.DECLINED);
        } catch (UsersRepositoryException e) {
            LOGGER.info("Unable to access UsersRepository", e);
        }
        return HookResult.DECLINED;
    }

    private HookResult doAuthWithDelegation(SMTPSession session, Username authenticatedUser, Username delegateUsername) {
        try {
            if (AuthorizationState.ALLOWED.equals(authorizator.user(authenticatedUser).canLoginAs(delegateUsername))) {
                users.assertValid(delegateUsername);
                session.setUsername(delegateUsername);
                session.setRelayingAllowed(true);
                return HookResult.builder()
                    .hookReturnCode(HookReturnCode.ok())
                    .smtpDescription("Authentication Successful.")
                    .build();
            }
        } catch (final MailboxException | UsersRepositoryException e) {
            LOGGER.info("Authorization failed", e);
        }
        return HookResult.DECLINED;
    }
}
