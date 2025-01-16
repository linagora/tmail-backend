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
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.DisconnectorNotifier.AllUsersRequest;
import org.apache.james.DisconnectorNotifier.MultipleUserRequest;
import org.apache.james.DisconnectorNotifier.Request;
import org.apache.james.core.Username;

import com.google.common.base.Splitter;

public class DisconnectorRequestSerializer {

    public static class DisconnectorRequestSerializeException extends RuntimeException {
        public DisconnectorRequestSerializeException(String message) {
            super(message);
        }

        public DisconnectorRequestSerializeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final String ALL_USERS_REQUEST = "[]";
    public static final String USER_DELIMITER = ",";

    public static String serialize(Request request) {
        return switch (request) {
            case MultipleUserRequest multipleUserRequest -> multipleUserRequest.usernameList().stream()
                .map(Username::asString)
                .collect(Collectors.joining(USER_DELIMITER, "[", "]"));
            case AllUsersRequest allUsersRequest -> ALL_USERS_REQUEST;
            default -> throw new DisconnectorRequestSerializeException("Unknown request type: " + request);
        };
    }

    public static byte[] serializeAsBytes(Request request) {
        return serialize(request).getBytes(StandardCharsets.UTF_8);
    }

    public static Request deserialize(String serialized) {
        if (ALL_USERS_REQUEST.equals(serialized)) {
            return AllUsersRequest.ALL_USERS_REQUEST;
        }
        if (StringUtils.startsWith(serialized, "[") && StringUtils.endsWith(serialized, "]")) {
            try {
                return MultipleUserRequest.of(Splitter.on(",")
                    .omitEmptyStrings()
                    .splitToStream(serialized.substring(1, serialized.length() - 1))
                    .map(Username::of)
                    .collect(Collectors.toSet()));
            } catch (Exception e) {
                throw new DisconnectorRequestSerializeException("Error while deserializing: " + serialized, e);
            }
        }
        throw new DisconnectorRequestSerializeException("Unknown serialized format: " + serialized);
    }

    public static Request deserialize(byte[] serialized) {
        return deserialize(new String(serialized, StandardCharsets.UTF_8));
    }
}
