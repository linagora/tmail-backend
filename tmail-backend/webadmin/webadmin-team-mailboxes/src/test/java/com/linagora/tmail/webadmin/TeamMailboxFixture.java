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

package com.linagora.tmail.webadmin;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;

import com.linagora.tmail.team.TeamMailbox;

import scala.jdk.javaapi.OptionConverters;

public class TeamMailboxFixture {
    public static Domain TEAM_MAILBOX_DOMAIN = Domain.of("linagora.com");
    public static Domain TEAM_MAILBOX_DOMAIN_2 = Domain.of("linagora2.com");
    public static TeamMailbox TEAM_MAILBOX = OptionConverters.toJava(TeamMailbox.fromJava(TEAM_MAILBOX_DOMAIN, "marketing")).orElseThrow();
    public static TeamMailbox TEAM_MAILBOX_2 = OptionConverters.toJava(TeamMailbox.fromJava(TEAM_MAILBOX_DOMAIN, "sale")).orElseThrow();
    public static Username TEAM_MAILBOX_USERNAME = Username.fromLocalPartWithDomain("team-mailbox", TEAM_MAILBOX_DOMAIN);
    public static Username TEAM_MAILBOX_USERNAME_2 = Username.fromLocalPartWithDomain("team-mailbox", TEAM_MAILBOX_DOMAIN_2);
    public static Username BOB = Username.of("bob@linagora.com");
    public static Username ANDRE = Username.of("andre@linagora.com");
}
