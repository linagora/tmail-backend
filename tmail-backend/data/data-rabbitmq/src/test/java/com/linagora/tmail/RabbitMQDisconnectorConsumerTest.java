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

package com.linagora.tmail;

import static org.apache.james.DisconnectorNotifier.AllUsersRequest.ALL_USERS_REQUEST;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Set;

import org.apache.james.DisconnectorNotifier.InVMDisconnectorNotifier;
import org.apache.james.DisconnectorNotifier.MultipleUserRequest;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Username;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RabbitMQDisconnectorConsumerTest {
    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    RabbitMQDisconnectorOperator operator;
    RabbitMQDisconnectorNotifier notifier;
    RabbitMQDisconnectorConsumer consumer;
    InVMDisconnectorNotifier inVmDisconnectorNotifier;

    @BeforeEach
    void setup() throws Exception {
        inVmDisconnectorNotifier = mock(InVMDisconnectorNotifier.class);

        DisconnectorRequestSerializer disconnectorRequestSerializer = new DisconnectorRequestSerializer();

        consumer = new RabbitMQDisconnectorConsumer(rabbitMQExtension.getReceiverProvider(),
            inVmDisconnectorNotifier,
            disconnectorRequestSerializer);

        operator = new RabbitMQDisconnectorOperator(rabbitMQExtension.getSender(),
            rabbitMQExtension.getRabbitMQ().getConfiguration(),
            consumer);

        operator.init();
        notifier = new RabbitMQDisconnectorNotifier(rabbitMQExtension.getSender(),
            disconnectorRequestSerializer);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void vmDisconnectorShouldDisconnectWhenNotifyRequestAllUser() {
        // when
        notifier.disconnect(ALL_USERS_REQUEST);

        // then
        verify(inVmDisconnectorNotifier, after(Duration.ofSeconds(1).toMillis())).disconnect(ALL_USERS_REQUEST);
    }

    @Test
    void vmDisconnectorShouldDisconnectWhenNotifyRequestMultipleUserWithSingleValue() {
        // when
        notifier.disconnect(MultipleUserRequest.of(Username.of("user1")));

        // then
        verify(inVmDisconnectorNotifier, after(Duration.ofSeconds(1).toMillis()))
            .disconnect(MultipleUserRequest.of(Username.of("user1")));
    }

    @Test
    void vmDisconnectorShouldDisconnectWhenNotifyRequestMultipleUserWithMultipleValues() {
        // when
        notifier.disconnect(MultipleUserRequest.of(Set.of(Username.of("user1"), Username.of("user2"))));

        // then
        verify(inVmDisconnectorNotifier, after(Duration.ofSeconds(1).toMillis()))
            .disconnect(MultipleUserRequest.of(Set.of(Username.of("user1"), Username.of("user2"))));
    }
}
