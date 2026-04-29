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

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;

import reactor.core.publisher.Mono;

public interface PromptRetriever {

    public record Prompts(String system, String userTemplate) {

    }

    public record PromptName(String value) {

    }

    public record Role(String value) {
        public Role {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Role must not be null/blank");
            }
        }

        public static Role system() {
            return new Role("system");
        }

        public static Role user() {
            return new Role("user");
        }

        public boolean equalsIgnoreCase(String other) {
            return other != null && value.equalsIgnoreCase(other);
        }
    }

    Mono<Prompts> retrievePrompts();

    interface Factory {
        PromptRetriever create(HierarchicalConfiguration<ImmutableNode> configuration);
    }

}
