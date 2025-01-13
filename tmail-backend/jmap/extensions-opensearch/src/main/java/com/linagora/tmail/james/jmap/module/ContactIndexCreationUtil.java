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

package com.linagora.tmail.james.jmap.module;

import java.util.Optional;

import org.apache.james.backends.opensearch.IndexCreationFactory;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;

import com.linagora.tmail.james.jmap.ContactMappingFactory;
import com.linagora.tmail.james.jmap.OpenSearchContactConfiguration;

public class ContactIndexCreationUtil {
    public static void createIndices(ReactorOpenSearchClient client,
                                     OpenSearchConfiguration openSearchConfiguration,
                                     OpenSearchContactConfiguration contactConfiguration) {
        ContactMappingFactory contactMappingFactory = new ContactMappingFactory(openSearchConfiguration, contactConfiguration);
        new IndexCreationFactory(openSearchConfiguration)
            .useIndex(contactConfiguration.getUserContactIndexName())
            .addAlias(contactConfiguration.getUserContactReadAliasName())
            .addAlias(contactConfiguration.getUserContactWriteAliasName())
            .createIndexAndAliases(client, Optional.of(contactMappingFactory.generalContactIndicesSetting()),
                Optional.of(contactMappingFactory.userContactMappingContent()));

        new IndexCreationFactory(openSearchConfiguration)
            .useIndex(contactConfiguration.getDomainContactIndexName())
            .addAlias(contactConfiguration.getDomainContactReadAliasName())
            .addAlias(contactConfiguration.getDomainContactWriteAliasName())
            .createIndexAndAliases(client, Optional.of(contactMappingFactory.generalContactIndicesSetting()),
                Optional.of(contactMappingFactory.domainContactMappingContent()));
    }
}
