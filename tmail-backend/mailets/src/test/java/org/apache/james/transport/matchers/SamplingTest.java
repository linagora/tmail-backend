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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;

import jakarta.mail.MessagingException;

import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

class SamplingTest {
    private static final String RECIPIENT = "test@james.apache.org";

    private Sampling matcher;

    @BeforeEach
    void setUp() {
        matcher = new Sampling();
    }

    @Test
    void initShouldAcceptValidSamplingRate() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("0.5")
            .build();

        assertThatCode(() -> matcher.init(matcherConfig)).doesNotThrowAnyException();
    }

    @Test
    void initShouldAcceptZeroSamplingRate() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("0.0")
            .build();

        assertThatCode(() -> matcher.init(matcherConfig)).doesNotThrowAnyException();
    }

    @Test
    void initShouldAcceptOneSamplingRate() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("1.0")
            .build();

        assertThatCode(() -> matcher.init(matcherConfig)).doesNotThrowAnyException();
    }

    @Test
    void initShouldThrowWhenConditionIsNull() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .build();

        assertThatThrownBy(() -> matcher.init(matcherConfig))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("Sampling rate must be specified");
    }

    @Test
    void initShouldThrowWhenConditionIsEmpty() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("")
            .build();

        assertThatThrownBy(() -> matcher.init(matcherConfig))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("Sampling rate must be specified");
    }

    @Test
    void initShouldThrowWhenConditionIsNotANumber() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("invalid")
            .build();

        assertThatThrownBy(() -> matcher.init(matcherConfig))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void initShouldThrowWhenSamplingRateIsNegative() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("-0.1")
            .build();

        assertThatThrownBy(() -> matcher.init(matcherConfig))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("Sampling rate must be between 0.0 and 1.0");
    }

    @Test
    void initShouldThrowWhenSamplingRateIsGreaterThanOne() {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("1.1")
            .build();

        assertThatThrownBy(() -> matcher.init(matcherConfig))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("Sampling rate must be between 0.0 and 1.0");
    }

    @Test
    void matchShouldNeverMatchWhenSamplingRateIsZero() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("0.0")
            .build();

        matcher.init(matcherConfig);

        IntStream.range(0, 100).forEach(Throwing.intConsumer(i -> {
            FakeMail mail = FakeMail.builder()
                .name("mail")
                .recipient(RECIPIENT)
                .build();

            assertThat(matcher.match(mail)).isEmpty();
        }));
    }

    @Test
    void matchShouldAlwaysMatchWhenSamplingRateIsOne() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("1.0")
            .build();

        matcher.init(matcherConfig);

        IntStream.range(0, 100).forEach(Throwing.intConsumer(i -> {
            FakeMail mail = FakeMail.builder()
                .name("mail")
                .recipient(RECIPIENT)
                .build();

            assertThat(matcher.match(mail)).isNotEmpty();
        }));
    }

    @Test
    void matchShouldMatchApproximatelyHalfOfEmailsWhenSamplingRateIsHalf() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("0.5")
            .build();

        matcher.init(matcherConfig);

        long matchCount = IntStream.range(0, 1000)
            .mapToObj(Throwing.intFunction(i -> FakeMail.builder()
                .name("mail-" + i)
                .recipient(RECIPIENT)
                .build()))
            .filter(Throwing.predicate(mail -> !matcher.match(mail).isEmpty()))
            .count();

        // With 1000 iterations and 0.5 sampling rate, we expect around 500 matches
        // Allow 10% margin of error (450-550)
        assertThat(matchCount).isBetween(450L, 550L);
    }

    @Test
    void matchShouldMatchApproximatelyOnePercentOfEmailsWhenSamplingRateIsOnePercent() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("Sampling")
            .condition("0.01")
            .build();

        matcher.init(matcherConfig);

        long matchCount = IntStream.range(0, 10000)
            .mapToObj(Throwing.intFunction(i -> FakeMail.builder()
                .name("mail-" + i)
                .recipient(RECIPIENT)
                .build()))
            .filter(Throwing.predicate(mail -> !matcher.match(mail).isEmpty()))
            .count();

        // With 10000 iterations and 0.01 sampling rate, we expect around 100 matches
        // Allow 30% margin of error (70-130) due to randomness
        assertThat(matchCount).isBetween(70L, 130L);
    }
}
