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

package org.apache.james.transport.matchers;

import static org.apache.james.transport.matchers.TransitedViaTest.RECEIVED_BY_EXAMPLE;
import static org.apache.james.transport.matchers.TransitedViaTest.RECEIVED_BY_TWAKE;
import static org.apache.james.transport.matchers.TransitedViaTest.RECIPIENT;
import static org.apache.james.transport.matchers.TransitedViaTest.mailWithReceivedHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransitedTwiceViaTest {
    private TransitedTwiceVia testee;

    @BeforeEach
    void setUp() throws MessagingException {
        testee = new TransitedTwiceVia();
        testee.init(FakeMatcherConfig.builder()
            .matcherName("TransitedTwiceVia")
            .condition("smtp.twake.app")
            .build());
    }

    @Test
    void initShouldThrowWhenNoCondition() {
        assertThatThrownBy(() -> new TransitedTwiceVia().init(FakeMatcherConfig.builder()
                .matcherName("TransitedTwiceVia")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldNotMatchWhenNoReceivedHeader() throws Exception {
        assertThat(testee.match(mailWithReceivedHeaders())).isEmpty();
    }

    @Test
    void shouldNotMatchWhenTransitedOnce() throws Exception {
        assertThat(testee.match(mailWithReceivedHeaders(RECEIVED_BY_TWAKE, RECEIVED_BY_EXAMPLE))).isEmpty();
    }

    @Test
    void shouldMatchWhenTransitedTwice() throws Exception {
        assertThat(testee.match(mailWithReceivedHeaders(RECEIVED_BY_TWAKE, RECEIVED_BY_EXAMPLE, RECEIVED_BY_TWAKE)))
            .containsOnly(new MailAddress(RECIPIENT));
    }

    @Test
    void shouldMatchWhenTransitedMoreThanTwice() throws Exception {
        assertThat(testee.match(mailWithReceivedHeaders(RECEIVED_BY_TWAKE, RECEIVED_BY_TWAKE, RECEIVED_BY_TWAKE)))
            .containsOnly(new MailAddress(RECIPIENT));
    }

    @Test
    void shouldCountReceivedHeadersRatherThanOccurrences() throws Exception {
        assertThat(testee.match(mailWithReceivedHeaders("from smtp.twake.app by smtp.twake.app with ESMTP")))
            .isEmpty();
    }
}
