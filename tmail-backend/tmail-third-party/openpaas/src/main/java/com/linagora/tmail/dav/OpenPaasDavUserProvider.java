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

package com.linagora.tmail.dav;

import jakarta.inject.Inject;

import org.apache.james.core.Username;

import com.linagora.tmail.api.OpenPaasRestClient;

import reactor.core.publisher.Mono;

public class OpenPaasDavUserProvider implements DavUserProvider {
    private final OpenPaasRestClient restClient;
    private final DavClient davClient;

    @Inject
    public OpenPaasDavUserProvider(OpenPaasRestClient restClient, DavClient davClient) {
        this.restClient = restClient;
        this.davClient = davClient;
    }

    @Override
    public Mono<DavUser> provide(Username username) {
        return provideDavUser(username)
            .switchIfEmpty(davClient.getPrincipal(username)
                .then(provideDavUser(username))
            .switchIfEmpty(Mono.error(() -> new RuntimeException("Unable to find user in Dav server with username '%s'".formatted(username.asString())))));
    }

    private Mono<DavUser> provideDavUser(Username username) {
        return restClient.searchOpenPaasUserId(username.asString())
            .map(openPaasUserId -> new DavUser(openPaasUserId, username.asString()));
    }
}
