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
