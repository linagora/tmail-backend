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

package com.linagora.tmail.migration.imap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapSaslExchangeTracker;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.junit.jupiter.api.Test;

class ProxyImapProcessorTest {
    private static ImapSession sessionWithAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        ImapSession session = mock(ImapSession.class);
        when(session.getAttribute(anyString())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            attributes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), any());
        return session;
    }

    @Test
    void pushContinuationHandlerShouldCloseAndReleaseExchangeWhenRegistrationFails() {
        ImapSession session = sessionWithAttributes();
        SaslExchange exchange = mock(SaslExchange.class);
        SaslExchange replacement = mock(SaslExchange.class);
        ImapLineHandler lineHandler = mock(ImapLineHandler.class);
        IllegalStateException failure = new IllegalStateException("registration failed");
        ImapSaslExchangeTracker tracker = ImapSaslExchangeTracker.forSession(session);
        tracker.register(exchange);
        doThrow(failure).when(session).pushLineHandler(lineHandler);

        assertThatThrownBy(() -> ProxyImapProcessor.pushContinuationHandler(session, exchange, lineHandler))
            .isSameAs(failure);

        verify(exchange).close();
        tracker.register(replacement);
        tracker.close();
        verify(replacement).close();
    }
}
