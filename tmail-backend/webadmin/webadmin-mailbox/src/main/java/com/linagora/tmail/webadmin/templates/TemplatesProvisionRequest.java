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

package com.linagora.tmail.webadmin.templates;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;

public class TemplatesProvisionRequest {
    private static final String ACTION_PARAM = "action";
    private static final String PROVISION_ACTION = "provision";
    private static final String FROM_PARAM = "from";
    private static final String FOLDER_NAME_PARAM = "folderName";
    private static final String OVERWRITE_EXISTING_PARAM = "overwriteExisting";
    private static final String PRUNE_PARAM = "prune";
    private static final String USERS_PER_SECOND_PARAM = "usersPerSecond";
    private static final int DEFAULT_USERS_PER_SECOND = 1;

    public static void assertProvisionAction(Request request) {
        String action = request.queryParams(ACTION_PARAM);
        if (!PROVISION_ACTION.equals(action)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Unknown action: '%s'. Supported actions: '%s'", action, PROVISION_ACTION)
                .haltError();
        }
    }

    public static Username parseSourceUser(Request request) {
        String from = request.queryParams(FROM_PARAM);
        if (from == null || from.isBlank()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("'%s' query parameter is required: the source account whose templates folder is the reference", FROM_PARAM)
                .haltError();
        }
        return parseUsername(from);
    }

    public static Username parseUsername(String rawUsername) {
        try {
            return Username.of(rawUsername);
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid username: '%s'", rawUsername)
                .cause(e)
                .haltError();
        }
    }

    public static String parseFolderName(Request request) {
        return Optional.ofNullable(request.queryParams(FOLDER_NAME_PARAM))
            .filter(folderName -> !folderName.isBlank())
            .orElse(DefaultMailboxes.TEMPLATES);
    }

    public static ProvisionOptions parseOptions(Request request) {
        return new ProvisionOptions(parseBoolean(request, OVERWRITE_EXISTING_PARAM), parseBoolean(request, PRUNE_PARAM));
    }

    public static int parseUsersPerSecond(Request request) {
        String rawUsersPerSecond = request.queryParams(USERS_PER_SECOND_PARAM);
        if (rawUsersPerSecond == null) {
            return DEFAULT_USERS_PER_SECOND;
        }
        try {
            int usersPerSecond = Integer.parseInt(rawUsersPerSecond);
            if (usersPerSecond <= 0) {
                throw new IllegalArgumentException("'usersPerSecond' needs to be strictly positive");
            }
            return usersPerSecond;
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid '%s' value '%s'. Expected a strictly positive integer.", USERS_PER_SECOND_PARAM, rawUsersPerSecond)
                .haltError();
        }
    }

    private static boolean parseBoolean(Request request, String parameterName) {
        return Optional.ofNullable(request.queryParams(parameterName))
            .map(value -> parseBooleanValue(parameterName, value))
            .orElse(false);
    }

    private static boolean parseBooleanValue(String parameterName, String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message("Invalid '%s' value '%s'. Expected 'true' or 'false'.", parameterName, value)
            .haltError();
    }
}
