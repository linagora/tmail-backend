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

package com.linagora.tmail.deployment;

import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.SmtpHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.apache.james.utils.SMTPMessageSender;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class TMailDeploymentValidation {

    public static final String DOMAIN = "domain";
    public static final String USER = "imapuser";
    public static final String USER_ADDRESS = USER + "@" + DOMAIN;
    public static final String PASSWORD = "password";
    private static final String INBOX = "INBOX";
    private static final String ONE_MAIL = "* 1 EXISTS";

    protected abstract ImapHostSystem createImapHostSystem();

    protected abstract SmtpHostSystem createSmtpHostSystem();

    protected abstract ExternalJamesConfiguration getConfiguration();

    private SmtpHostSystem smtpSystem;
    private SimpleScriptedTestProtocol simpleScriptedTestProtocol;
    private final IMAPClient imapClient = new IMAPClient();

    protected static final Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    protected static final ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    protected static final ConditionFactory awaitAtMostTenSeconds = calmlyAwait.atMost(TEN_SECONDS);

    @BeforeEach
    public void setUp() throws Exception {
        ImapHostSystem system = createImapHostSystem();
        smtpSystem = createSmtpHostSystem();

        simpleScriptedTestProtocol = new SimpleScriptedTestProtocol("/com/linagora/tmail/deployment/scripts/", system)
            .withUser(USER_ADDRESS, PASSWORD)
            .withLocale(Locale.US);
    }

    @Test
    public void validateDeployment() throws Exception {
        simpleScriptedTestProtocol.run("ValidateDeployment");
    }

    @Test
    public void selectThenFetchWithExistingMessages() throws Exception {
        simpleScriptedTestProtocol.run("SelectThenFetchWithExistingMessages");
    }

    @Test
    public void validateDeploymentWithMailsFromSmtp() throws Exception {
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender("another-domain");
        smtpSystem.connect(smtpMessageSender)
            .authenticate(USER_ADDRESS, PASSWORD)
            .sendMessage(USER_ADDRESS, USER_ADDRESS);
        imapClient.connect(getConfiguration().getAddress(), getConfiguration().getImapPort().getValue());
        imapClient.login(USER_ADDRESS, PASSWORD);
        awaitAtMostTenSeconds.until(this::checkMailDelivery);
    }

    private Boolean checkMailDelivery() throws IOException {
        imapClient.select(INBOX);
        String replyString = imapClient.getReplyString();
        return replyString.contains(ONE_MAIL);
    }
}
