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

package com.linagora.tmail.route;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.inject.Inject;

import org.apache.james.util.DurationParser;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.google.common.base.Preconditions;
import com.linagora.tmail.api.MailReportEntry;
import com.linagora.tmail.api.MailReportGenerator;

import spark.Service;

public class MailReportsRoute implements Routes {
    public record MailReportEntryDAO(MailReportEntry.Kind kind,
                                     String subject,
                                     String sender,
                                     String recipient,
                                     Instant date,
                                     long size) {
        public static MailReportEntryDAO from(MailReportEntry entry) {
            return new MailReportEntryDAO(entry.kind(), entry.subject(), entry.sender().asString("<>"),
                entry.recipient().asString(), entry.date(), entry.size());
        }
    }

    public static final String BASE_PATH = "/reports";

    private final MailReportGenerator receivedMailReportGenerator;
    private final Clock clock;

    @Inject
    public MailReportsRoute(MailReportGenerator receivedMailReportGenerator, Clock clock) {
        this.receivedMailReportGenerator = receivedMailReportGenerator;
        this.clock = clock;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        JsonTransformer transformer = new JsonTransformer();
        service.get(getBasePath() + "/mails", (request, response) -> {
            String rawDuration = request.queryParams("duration");
            Preconditions.checkArgument(rawDuration != null, "'duration' is a mandatory parameter");
            Duration duration = DurationParser.parse(rawDuration);
            Instant now = clock.instant();
            Instant reportStart = now.minus(duration);
            return receivedMailReportGenerator.generateReport(reportStart, now)
                .map(MailReportEntryDAO::from)
                .collectList()
                .block();
        }, transformer);
    }
}