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
 *******************************************************************/

package com.linagora.tmail.saas.api;

import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DomainSendingValidator {
    public static final String PENDING_ACTIVATION = "Domain activation is pending: mail DNS configuration is not validated";

    private final SaaSAccountRepository repository;

    @Inject
    public DomainSendingValidator(SaaSAccountRepository repository) {
        this.repository = repository;
    }

    public Mono<Boolean> canSend(Optional<Username> authenticatedUser, MaybeSender sender) {
        return Flux.fromStream(() -> Stream.concat(
                authenticatedUser.flatMap(Username::getDomainPart).stream(),
                sender.asOptional().map(MailAddress::getDomain).stream()).distinct())
            .concatMap(this::canSend)
            .all(Boolean::booleanValue);
    }

    private Mono<Boolean> canSend(Domain domain) {
        // Existing and non-SaaS domains have no DNS state. Only SaaS provisioning sets it.
        return Mono.from(repository.getMailDnsConfigurationValidated(domain)).defaultIfEmpty(true);
    }
}
