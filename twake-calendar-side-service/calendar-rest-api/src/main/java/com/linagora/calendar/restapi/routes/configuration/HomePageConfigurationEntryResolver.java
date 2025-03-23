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

package com.linagora.calendar.restapi.routes.configuration;

import org.apache.james.mailbox.MailboxSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.linagora.calendar.restapi.api.ConfigurationEntryResolver;
import com.linagora.calendar.restapi.api.ConfigurationKey;
import com.linagora.calendar.restapi.api.ModuleName;

import reactor.core.publisher.Mono;

public class HomePageConfigurationEntryResolver implements ConfigurationEntryResolver {
    @Override
    public Mono<JsonNode> resolve(MailboxSession session) {
        return Mono.just(NullNode.getInstance());
    }

    @Override
    public ModuleName moduleName() {
        return new ModuleName("core");
    }

    @Override
    public ConfigurationKey configurationKey() {
        return new ConfigurationKey("homePage");
    }
}
