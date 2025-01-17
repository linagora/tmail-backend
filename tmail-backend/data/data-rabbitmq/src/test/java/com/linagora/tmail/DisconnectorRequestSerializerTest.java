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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.DisconnectorNotifier.AllUsersRequest;
import org.apache.james.DisconnectorNotifier.MultipleUserRequest;
import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.javacrumbs.jsonunit.core.Option;

public class DisconnectorRequestSerializerTest {

    private final DisconnectorRequestSerializer testee = new DisconnectorRequestSerializer();

    @Test
    void serializeAllUsersRequestShouldReturnEmptyArray() throws JsonProcessingException {
        assertThat(new String(testee.serialize(new AllUsersRequest()), StandardCharsets.UTF_8))
            .isEqualTo("[]");
    }

    @Test
    void deserializeAllUsersRequestShouldReturnEmptyArray() {
        assertThat(testee.deserialize("[]".getBytes(StandardCharsets.UTF_8)))
            .isEqualTo(new AllUsersRequest());
    }

    @Test
    void serializeMultipleUserRequestShouldReturnSerializedRequest() throws JsonProcessingException {
        MultipleUserRequest multipleUserRequest = MultipleUserRequest.of(Set.of(Username.of("user1@domain.tld"), Username.of("user2@domain2.tld")));
        assertThatJson(new String(testee.serialize(multipleUserRequest), StandardCharsets.UTF_8))
            .withOptions(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo("""
                ["user1@domain.tld","user2@domain2.tld"]""");
    }

    @Test
    void deserializeMultipleUserRequestShouldReturnDeserializedRequest() {
        DisconnectorNotifier.Request deserialize = testee.deserialize("""
            ["user1@domain.tld","user2@domain2.tld"]""".getBytes(StandardCharsets.UTF_8));
        assertThat(deserialize).isInstanceOf(MultipleUserRequest.class)
            .satisfies(request -> assertThat(((MultipleUserRequest) request).usernameList())
                .containsExactlyInAnyOrder(Username.of("user1@domain.tld"), Username.of("user2@domain2.tld")));
    }

    @Test
    void deserializeOfSerializedMultipleUserRequestShouldReturnOriginalRequest() throws JsonProcessingException {
        // generate random 10 Usernames
        Set<Username> usernameSet = IntStream.range(0, 10)
            .mapToObj(i -> Username.of("user" + i + "@abc.com" + UUID.randomUUID()))
            .collect(Collectors.toSet());
        assertThat(testee.deserialize(testee.serialize(MultipleUserRequest.of(usernameSet))))
            .isEqualTo(MultipleUserRequest.of(usernameSet));
    }

    @Test
    void shouldDeserializeFailedWhenInvalidJson() {
        assertThatThrownBy(() -> testee.deserialize("invalid".getBytes(StandardCharsets.UTF_8)))
            .hasMessageContaining("Error while deserializing: invalid")
            .isInstanceOf(DisconnectorRequestSerializer.DisconnectorRequestSerializeException.class);
    }
}
