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

package com.linagora.tmail.imap;

import org.apache.james.core.Username;

import com.linagora.tmail.team.TeamMailbox;

/**
 * An authorized team mailbox impersonation: the {@code teamMailbox} to scope the session to, and the
 * {@code sessionUser} the session should be authenticated as (the member himself, or the team mailbox
 * owner when an administrator impersonates it).
 */
public record TeamMailboxImpersonation(TeamMailbox teamMailbox, Username sessionUser) {
}
