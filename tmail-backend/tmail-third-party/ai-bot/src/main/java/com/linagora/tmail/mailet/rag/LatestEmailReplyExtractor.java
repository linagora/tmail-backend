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

import com.google.common.base.Splitter;

public interface LatestEmailReplyExtractor {

    class RegexBased implements LatestEmailReplyExtractor {

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

        @Override
        public String cleanQuotedContent(String content) {
            if (content == null || content.trim().isEmpty()) {
                return "";
            }
            String normalized = content.replace("\r\n", "\n").replace("\r", "\n");

            StringBuilder result = new StringBuilder();
            for (String line : Splitter.on('\n').split(normalized)) {
                if (QUOTE_PATTERNS.matcher(line).find()) {
                    break;
                }
                if (line.trim().isEmpty() && result.length() == 0) {
                    continue;
                }
                result.append(line.strip()).append("\n");
            }

            removeTrailingNewlines(result);

            return result.toString();
        }

        private static void removeTrailingNewlines(StringBuilder sb) {
            if (sb == null) {
                return;
            }
            while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                sb.setLength(sb.length() - 1);
            }
        }
    }

    public String cleanQuotedContent(String content);
}