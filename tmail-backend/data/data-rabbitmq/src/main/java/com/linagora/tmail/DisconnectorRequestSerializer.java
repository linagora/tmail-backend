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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.DisconnectorNotifier.AllUsersRequest;
import org.apache.james.DisconnectorNotifier.MultipleUserRequest;
import org.apache.james.DisconnectorNotifier.Request;
import org.apache.james.core.Username;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DisconnectorRequestSerializer {

    public static class DisconnectorRequestSerializeException extends RuntimeException {

        public DisconnectorRequestSerializeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final String ALL_USERS_REQUEST = "[]";
    public static final byte[] ALL_USERS_REQUEST_BYTES = ALL_USERS_REQUEST.getBytes(StandardCharsets.UTF_8);
    public static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Inject
    @Singleton
    public DisconnectorRequestSerializer() {
        this.objectMapper = new ObjectMapper();
    }

    public byte[] serialize(Request request) throws JsonProcessingException {
        return switch (request) {
            case MultipleUserRequest multipleUserRequest -> objectMapper.writeValueAsBytes(
                multipleUserRequest.usernameList().stream()
                    .map(Username::asString)
                    .toList());
            case AllUsersRequest allUsersRequest -> ALL_USERS_REQUEST_BYTES;
        };
    }

    public Request deserialize(byte[] serialized) {
        if (serialized.length == 2 && serialized[0] == '[' && serialized[1] == ']') {
            return AllUsersRequest.ALL_USERS_REQUEST;
        }
        try {
            Set<Username> usernameSet = objectMapper.readValue(serialized, LIST_OF_STRING)
                .stream().map(Username::of)
                .collect(Collectors.toSet());
            return new MultipleUserRequest(usernameSet);
        } catch (Exception e) {
            throw new DisconnectorRequestSerializeException("Error while deserializing: " + new String(serialized, StandardCharsets.UTF_8), e);
        }
    }
}
