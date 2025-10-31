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

import java.util.regex.Pattern;

public class EmailParser {

    private static final Pattern QUOTE_PATTERNS = Pattern.compile(
        "(?m)^\\s*>.*$|" +
            "(?m)^On .+wrote:.*$|" +
            "(?m)^Le .+a écrit.*$|" +
            "(?m)^From:.*$|" +
            "(?m)^Sent:.*$|" +
            "(?m)^To:.*$|" +
            "(?m)^Subject:.*$"
    );

    public String cleanQuotedContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        System.out.println("Im hereeeeeee\n");
        String[] lines = content.split("\n");
        System.out.println("Im hereeeeeee\n");
        for (String line : lines) {
            System.out.println("Line: " + line + "\n");
        }
        StringBuilder result = new StringBuilder();

        for (String line : lines) {

            if (line.trim().startsWith(">") ||
                line.matches("^On .+wrote:.*") ||
                line.matches("^Le .+a écrit.*") ||
                line.matches("^From:.*") ||
                line.matches("^Sent:.*") ||
                line.matches("^To:.*") ||
                line.matches("^Subject:.*")) {
                break;
            }
            result.append(line).append("\n");
        }
        System.out.println("Result: " + result.toString() + "\n");
        return result.toString().trim();
    }
}
