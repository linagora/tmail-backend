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

import jakarta.inject.Inject;

import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.esmtp.EhloExtension;

/**
 * Announces the ESMTP extensions listed in {@code submission.ehlo.extensions}. The client never sees
 * the EHLO reply of the backend it ends up relayed to, so the proxy announces on its behalf.
 */
public class SubmissionEhloExtensions implements EhloExtension, ProtocolHandler {
    private final SubmissionProxyConfiguration configuration;

    @Inject
    public SubmissionEhloExtensions(SubmissionProxyConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        return configuration.ehloExtensions();
    }
}
