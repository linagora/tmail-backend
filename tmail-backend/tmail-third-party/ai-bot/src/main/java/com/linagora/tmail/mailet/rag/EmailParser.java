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
        "(?mi)" +
            "^\\s*>.*$|" +
            "^On .+wrote:.*$|" +
            "^Le .+a écrit.*$|" +
            "^De :.*$|" +
            "^Envoyé :.*$|" +
            "^À :.*$|" +
            "^Objet :.*$|" +
            "^From:.*$|" +
            "^Sent:.*$|" +
            "^To:.*$|" +
            "^Subject:.*$|" +
            "^On .+from .+<.*>.*$|" +
            "^Le .+de .+<.*>.*$|" +
            "^On .+, at .+<.*> wrote:.*$|" +
            "^Le .+à .+<.*> a écrit.*$"
    );

    public String cleanQuotedContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            if (QUOTE_PATTERNS.matcher(line).find()) {
                break;
            }
            result.append(line).append("\n");
        }
        return result.toString().trim();
    }
}
