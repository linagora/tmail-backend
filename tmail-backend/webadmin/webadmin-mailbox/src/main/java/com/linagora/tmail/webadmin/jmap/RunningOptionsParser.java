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

package com.linagora.tmail.webadmin.jmap;

import java.util.Optional;

import spark.Request;

public class RunningOptionsParser {
    public static RunningOptions parse(Request request) {
        return intQueryParameter(request, "messagesPerSecond")
            .map(RunningOptions::withMessageRatePerSecond)
            .orElse(RunningOptions.DEFAULT);
    }

    public static Optional<Integer> intQueryParameter(Request request, String queryParameter) {
        try {
            return Optional.ofNullable(request.queryParams(queryParameter))
                .map(Integer::parseInt);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Illegal value supplied for query parameter '%s', expecting a " +
                "strictly positive optional integer", queryParameter), e);
        }
    }
}
