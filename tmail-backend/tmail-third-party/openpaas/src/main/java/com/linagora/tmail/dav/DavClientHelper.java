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

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;

import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClientResponse;

class DavClientHelper {

    static String authenticationToken(DavConfiguration config, String username) {
        return HttpUtils.createBasicAuthenticationToken(new UsernamePasswordCredentials(
            config.adminCredential().getUserName() + "&" + username,
            config.adminCredential().getPassword()));
    }

    static <T> Mono<T> unexpectedStatus(HttpClientResponse response, ByteBufMono content, String context) {
        return content.asString(StandardCharsets.UTF_8)
            .switchIfEmpty(Mono.just(StringUtils.EMPTY))
            .flatMap(body -> Mono.error(new DavClientException(
                "Unexpected status code: %d when %s: %s".formatted(response.status().code(), context, body))));
    }
}
