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

import java.util.Optional;

import reactor.core.publisher.Mono;

public interface PromptRetriever {

    public record Prompts(Optional<String> system, Optional<String> user) {

        public Prompts {
            system = system.map(String::trim).filter(s -> !s.isBlank());
            user = user.map(String::trim).filter(s -> !s.isBlank());
        }

        public String systemOrThrow() {
            return system.orElseThrow(() ->
                new PromptRetrievalException("No system prompt found "));
        }

        public String userOrThrow() {
            return user.orElseThrow(() ->
                new PromptRetrievalException("No user prompt found "));
        }

        public static Prompts empty() {
            return new Prompts(Optional.empty(), Optional.empty());
        }

        public static Prompts ofSystem(String system) {
            return new Prompts(Optional.ofNullable(system), Optional.empty());
        }

        public static Prompts ofUser(String user) {
            return new Prompts(Optional.empty(), Optional.ofNullable(user));
        }

        public static Prompts of(Optional<String> system, Optional<String> user) {
            return new Prompts(system, user);
        }
    }

    Mono<Prompts> retrievePrompts();

}
