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


package com.linagora.tmail.migration.submission;

import java.util.List;

import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.api.handler.CommandHandlerResultLogger;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.smtp.core.HeloCmdHandler;
import org.apache.james.protocols.smtp.core.NoopCmdHandler;
import org.apache.james.protocols.smtp.core.QuitCmdHandler;
import org.apache.james.protocols.smtp.core.RsetCmdHandler;
import org.apache.james.protocols.smtp.core.UnknownCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.EhloCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.StartTlsCmdHandler;
import org.apache.james.smtpserver.JamesWelcomeMessageHandler;

/**
 * The whole command set of the submission proxy, which only takes a client up to {@code AUTH}: no mail
 * transaction, no hook, no relaying decision. Everything after {@code AUTH} is the backend's business.
 */
public class SubmissionProxyHandlersLoader implements HandlersPackage {
    private static final List<String> HANDLERS = List.of(
        JamesWelcomeMessageHandler.class.getName(),
        CommandDispatcher.class.getName(),
        EhloCmdHandler.class.getName(),
        HeloCmdHandler.class.getName(),
        StartTlsCmdHandler.class.getName(),
        SubmissionAuthCmdHandler.class.getName(),
        SubmissionEhloExtensions.class.getName(),
        SubmissionAuthRequiredCmdHandler.class.getName(),
        NoopCmdHandler.class.getName(),
        RsetCmdHandler.class.getName(),
        QuitCmdHandler.class.getName(),
        UnknownCmdHandler.class.getName(),
        CommandHandlerResultLogger.class.getName());

    @Override
    public List<String> getHandlers() {
        return HANDLERS;
    }
}
