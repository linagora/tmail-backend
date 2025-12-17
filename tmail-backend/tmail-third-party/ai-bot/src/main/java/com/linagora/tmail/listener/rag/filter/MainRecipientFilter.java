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

package com.linagora.tmail.listener.rag.filter;

import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;

import reactor.core.publisher.Mono;

public class MainRecipientFilter implements MessageFilter {

    @Override
    public Mono<Boolean> matches(FilterContext context) {
        return Mono.fromCallable(() -> {
            String userAddress = context.session().getUser().asString();
            AddressList toList = context.mimeMessage().getTo();

            if (toList == null) {
                return false;
            }

            return toList.flatten()
                .stream()
                .map(Mailbox::getAddress)
                .anyMatch(address -> address.equalsIgnoreCase(userAddress));
        });
    }
}
