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
package com.linagora.tmail.listener.rag.prompt;

import java.net.URL;
import java.util.Optional;

import reactor.core.publisher.Mono;


public interface PromptRetriever {

    public record Prompts(Optional<String> system, Optional<String> user) {

        public Prompts {
            system = system.map(String::trim).filter(s -> !s.isBlank());
            user = user.map(String::trim).filter(s -> !s.isBlank());
        }

        public String systemOrThrow(String promptName) {
            return system.orElseThrow(() ->
                new PromptRetrievalException("No system prompt found for promptName='" + promptName + "'"));
        }

        public String userOrThrow(String promptName) {
            return user.orElseThrow(() ->
                new PromptRetrievalException("No user prompt found for promptName='" + promptName + "'"));
        }
    }
    Mono<Prompts> retrievePrompts(URL url, String promptName);

}
