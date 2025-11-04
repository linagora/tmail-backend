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
package com.linagora.tmail.mailet.rag;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;


public class LatestEmailReplyExtractorBenchmark {

    private static final LatestEmailReplyExtractor LATEST_EMAIL_REPLY_EXTRACTOR = new LatestEmailReplyExtractor.RegexBased();

    @State(Scope.Benchmark)
    public static class EmailSamples {
        public final String gmail = """
            Sure, I’ll handle this today.

            On Tue, Oct 10, 2023 at 2:15 PM Jane Smith <jane@example.com> wrote:
            The meeting has been rescheduled to 3 PM.
            """;

        public final String outlook = """
            Here is my final answer.

            From: support@company.com
            Sent: Monday, October 10, 2023 10:00 AM
            To: user@example.com
            Subject: Request for confirmation
            """;

        public final String apple = """
            Thanks for the update.

            On Oct 28, 2025, at 3:35 PM, Alae Mghirbi <amghirbi@linagora.com> wrote:
            Hi Sam,

            Glad to hear you’re doing well!
            """;

        public final String tmail = """
            Hi Ale,

            Excellent, thank you for the quick follow-up and the data file.
            I've downloaded it and will begin the analysis shortly.

            Sounds good. I aim to have a first draft to you by Wednesday afternoon
            for your initial thoughts.

            Talk soon,
            Sam

            On Oct 28, 2025 3:35 pm, from Alae Mghirbi <amghirbi@linagora.com>, <amghirbi@linagora.com>
            Hi Sam,
            """;

        public final String largeEmail;

        public final String largeEmailWithReply;

        public EmailSamples() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("This is line ").append(i).append(": Hello Alae, thanks for the update!\n");
            }
            sb.append("\nOn Tue, Oct 10, 2023 at 2:15 PM Jane Smith <jane@example.com> wrote:\n");
            sb.append("Previous message...");
            largeEmail = sb.toString();

            StringBuilder withReply = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                withReply.append("This is new message line ").append(i)
                    .append(": Hi Alae, here’s today’s update.\n");
            }

            withReply.append("\nOn Tue, Oct 10, 2023 at 2:15 PM Jane Smith <jane@example.com> wrote:\n");
            withReply.append("Sure, here’s the file you asked for.\n");
            withReply.append("Best,\nJane\n\n");

            for (int i = 500; i < 1000; i++) {
                withReply.append("> This is old quoted line ").append(i)
                    .append(": Previous message continues...\n");
            }

            largeEmailWithReply = withReply.toString();
        }
    }


    @Test
    public void launchBenchmark() throws Exception {
        Options opt = new OptionsBuilder()
            .include(this.getClass().getName() + ".*")
            .mode (Mode.AverageTime)
            .timeUnit(TimeUnit.MICROSECONDS)
            .warmupTime(TimeValue.seconds(5))
            .warmupIterations(3)
            .measurementTime(TimeValue.seconds(2))
            .measurementIterations(5)
            .threads(1)
            .forks(1)
            .shouldFailOnError(true)
            .shouldDoGC(true)
            .build();

        new Runner(opt).run();
    }

    @Benchmark
    public String benchmarkGmailFormat(EmailSamples samples) {
        return LATEST_EMAIL_REPLY_EXTRACTOR.cleanQuotedContent(samples.gmail);
    }

    @Benchmark
    public String benchmarkOutlookFormat(EmailSamples samples) {
        return LATEST_EMAIL_REPLY_EXTRACTOR.cleanQuotedContent(samples.outlook);
    }

    @Benchmark
    public String benchmarkAppleFormat(EmailSamples samples) {
        return LATEST_EMAIL_REPLY_EXTRACTOR.cleanQuotedContent(samples.apple);
    }

    @Benchmark
    public String benchmarkTmailFormat(EmailSamples samples) {
        return LATEST_EMAIL_REPLY_EXTRACTOR.cleanQuotedContent(samples.tmail);
    }

    @Benchmark
    public String benchmarkLargeEmail(EmailSamples samples) {
        return LATEST_EMAIL_REPLY_EXTRACTOR.cleanQuotedContent(samples.largeEmail);
    }

    @Benchmark
    public String benchmarkLargeEmailWithReply(EmailSamples samples) {
        return LATEST_EMAIL_REPLY_EXTRACTOR.cleanQuotedContent(samples.largeEmailWithReply);
    }

}
