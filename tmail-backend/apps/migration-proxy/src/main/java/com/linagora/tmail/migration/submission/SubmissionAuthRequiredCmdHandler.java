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

import java.util.Collection;

import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPSession;

import com.google.common.collect.ImmutableSet;

/**
 * The mail transaction commands only ever reach the proxy before {@code AUTH}: once authenticated, the
 * connection is relayed to the backend. The proxy itself never accepts mail.
 */
public class SubmissionAuthRequiredCmdHandler implements CommandHandler<SMTPSession> {
    static final Response AUTHENTICATION_REQUIRED = new SMTPResponse("530", "5.7.0 Authentication required").immutable();

    @Override
    public Collection<String> getImplCommands() {
        return ImmutableSet.of("MAIL", "RCPT", "DATA", "BDAT");
    }

    @Override
    public Response onCommand(SMTPSession session, Request request) {
        return AUTHENTICATION_REQUIRED;
    }
}
