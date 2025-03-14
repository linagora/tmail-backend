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

package com.linagora.tmail.dav.cal;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.Instant;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FreeBusySerializerTest {

    private FreeBusySerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new FreeBusySerializer();
    }

    @Test
    void serializeRequestShouldSuccess() {
        FreeBusyRequest request = new FreeBusyRequest(
            Instant.parse("2025-03-08T02:35:00Z"),
            Instant.parse("2025-03-08T03:15:00Z"),
            List.of("67c913533f46f500576ed03e"),
            List.of("b787cb16-fbe8-478f-8877-c699f9e314d8")
        );

        assertThatJson(serializer.serialize(request))
            .isEqualTo("""
                {
                    "start": "20250308T023500Z",
                    "end": "20250308T031500Z",
                    "users": ["67c913533f46f500576ed03e"],
                    "uids": ["b787cb16-fbe8-478f-8877-c699f9e314d8"]
                }""");
    }

    @Test
    void deserializeResponseShouldSuccess() throws Exception {
        String json = """
            {
                 "start": "20250308T023500Z",
                 "end": "20250310T031500Z",
                 "users": [
                     {
                         "id": "67c913533f46f500576ed03e",
                         "calendars": [
                             {
                                 "id": "67c913533f46f500576ed03e",
                                 "busy": [
                                     {
                                         "uid": "2213afbb-d7c4-48fd-a7a4-919c56b74111",
                                         "start": "20250308T023000Z",
                                         "end": "20250308T030000Z"
                                     },
                                     {
                                         "uid": "2213afbb-d7c4-48fd-a7a4-919c56b74222",
                                         "start": "20250309T023000Z",
                                         "end": "20250309T030000Z"
                                     }
                                 ]
                             }
                         ]
                     }
                 ]
             }""";

        FreeBusyResponse response = serializer.deserialize(json);
        assertThat(response.start()).isEqualTo(Instant.parse("2025-03-08T02:35:00Z"));
        assertThat(response.end()).isEqualTo(Instant.parse("2025-03-10T03:15:00Z"));

        List<FreeBusyResponse.User> users = response.users();
        Assertions.assertThat(users).hasSize(1);
        assertThat(users.getFirst().id()).isEqualTo("67c913533f46f500576ed03e");

        List<FreeBusyResponse.Calendar> calendars = users.getFirst().calendars();
        Assertions.assertThat(calendars).hasSize(1);
        assertThat(calendars.getFirst().id()).isEqualTo("67c913533f46f500576ed03e");

        List<FreeBusyResponse.BusyTime> busyTimes = calendars.getFirst().busy();
        Assertions.assertThat(busyTimes).hasSize(2);

        Assertions.assertThat(busyTimes)
            .containsExactlyInAnyOrder(new FreeBusyResponse.BusyTime(
                "2213afbb-d7c4-48fd-a7a4-919c56b74111",
                Instant.parse("2025-03-08T02:30:00Z"),
                Instant.parse("2025-03-08T03:00:00Z")),
                new FreeBusyResponse.BusyTime(
                    "2213afbb-d7c4-48fd-a7a4-919c56b74222",
                    Instant.parse("2025-03-09T02:30:00Z"),
                    Instant.parse("2025-03-09T03:00:00Z"
            )));
    }
}
