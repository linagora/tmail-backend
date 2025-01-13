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

package com.linagora.tmail.contact;

import jakarta.inject.Inject;

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.reactivestreams.Publisher;

import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;

public class OpenPaasContactsConsumerReconnectionHandler implements SimpleConnectionPool.ReconnectionHandler {
    private final OpenPaasContactsConsumer openPaasContactsConsumer;

    @Inject
    public OpenPaasContactsConsumerReconnectionHandler(OpenPaasContactsConsumer openPaasContactsConsumer) {
        this.openPaasContactsConsumer = openPaasContactsConsumer;
    }

    @Override
    public Publisher<Void> handleReconnection(Connection connection) {
        return Mono.fromRunnable(openPaasContactsConsumer::restart);
    }
}