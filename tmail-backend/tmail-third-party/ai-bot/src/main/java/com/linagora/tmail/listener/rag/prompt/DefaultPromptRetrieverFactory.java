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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;

public class DefaultPromptRetrieverFactory implements PromptRetriever.Factory {

    @Override
    public PromptRetriever create(HierarchicalConfiguration<ImmutableNode> configuration) {
        PromptRetrieverConfiguration configPromptRetriever = PromptRetrieverConfiguration.from(configuration);

        if (configPromptRetriever.getSystemPromptUrl().isEmpty()) {
            return new InlinePromptRetriever(configPromptRetriever.getInlinePrompts());
        }

        URL systemPromptUrl = baseURLStringToURL(configPromptRetriever.getSystemPromptUrl().get())
            .orElseThrow(() -> new IllegalArgumentException("Invalid system prompt URL"));

        return new HttpPromptRetriever(systemPromptUrl, configPromptRetriever.getPromptName());
    }

    private static Optional<URL> baseURLStringToURL(String baseUrlString) {
        try {
            return Optional.of(URI.create(baseUrlString).toURL());
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid prompt URL", e);
        }
    }
}