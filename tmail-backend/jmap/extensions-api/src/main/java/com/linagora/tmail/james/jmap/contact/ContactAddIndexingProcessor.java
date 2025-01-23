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

package com.linagora.tmail.james.jmap.contact;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public interface ContactAddIndexingProcessor {

    Publisher<Void> process(Username username, ContactFields contactFields);

    class Default implements ContactAddIndexingProcessor {
        private final EmailAddressContactSearchEngine contactIndexingService;

        @Inject
        @Singleton
        public Default(EmailAddressContactSearchEngine contactIndexingService) {
            this.contactIndexingService = contactIndexingService;
        }

        @Override
        public Publisher<Void> process(Username username, ContactFields contactFields) {
            return Mono.from(contactIndexingService.index(AccountId.fromUsername(username), contactFields))
                .then();
        }
    }
}
