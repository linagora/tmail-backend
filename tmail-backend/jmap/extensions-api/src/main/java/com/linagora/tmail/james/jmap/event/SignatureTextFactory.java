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
 *******************************************************************/

package com.linagora.tmail.james.jmap.event;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.core.Username;

import com.google.common.html.HtmlEscapers;

import reactor.core.publisher.Mono;

public interface SignatureTextFactory {
    record SignatureText(String textSignature, String htmlSignature) {
        private static final Pattern LDAP_PLACEHOLDER = Pattern.compile("\\{ldap:([^}]+)\\}");

        /**
         * Directory values are escaped when substituted into the HTML signature: they must not inject markup.
         * Placeholders match attribute names case-insensitively, as LDAP attribute descriptions are (RFC 4512 §2.5).
         */
        public SignatureText interpolate(Map<String, String> ldapAttributes) {
            Map<String, String> caseInsensitiveAttributes = caseInsensitive(ldapAttributes);
            return new SignatureText(
                interpolateSingle(textSignature, caseInsensitiveAttributes, UnaryOperator.identity()),
                interpolateSingle(htmlSignature, caseInsensitiveAttributes, HtmlEscapers.htmlEscaper()::escape));
        }

        private static Map<String, String> caseInsensitive(Map<String, String> ldapAttributes) {
            Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            result.putAll(ldapAttributes);
            return result;
        }

        private static String interpolateSingle(String template, Map<String, String> ldapAttributes, UnaryOperator<String> escaper) {
            if (template == null) {
                return null;
            }
            if (template.indexOf('{') < 0) {
                return template;
            }
            Matcher matcher = LDAP_PLACEHOLDER.matcher(template);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                String attributeName = matcher.group(1);
                String replacement = Optional.ofNullable(ldapAttributes.get(attributeName))
                    .map(escaper)
                    .orElse(matcher.group(0));
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(result);
            return result.toString();
        }
    }

    Mono<Optional<SignatureText>> forUser(Username username);
}
