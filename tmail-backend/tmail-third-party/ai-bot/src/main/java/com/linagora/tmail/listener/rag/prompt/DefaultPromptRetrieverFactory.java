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

public class DefaultPromptRetrieverFactory implements PromptRetriever.Factory {

    @Override
    public PromptRetriever create(HierarchicalConfiguration<ImmutableNode> configuration) {
        ConfigurationPromptRetriever configPromptRetriever = ConfigurationPromptRetriever.from(configuration);

        if (configPromptRetriever.getSystemPromptUrl().isEmpty()) {
            return configPromptRetriever;
        }
        return new HttpPromptRetriever(configPromptRetriever.getSystemPromptUrl().get(), configPromptRetriever.getPromptName());
    }
}