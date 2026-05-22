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

package com.linagora.tmail.webadmin.label;

import java.util.Optional;

import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.james.jmap.model.Color;
import com.linagora.tmail.james.jmap.model.DisplayName;

public record UpdateLabelRequest(DisplayName displayName, Optional<Color> color, Optional<String> description, Optional<Boolean> readOnly) {

    @JsonCreator
    public UpdateLabelRequest(
        @JsonProperty("displayName") String displayName,
        @JsonProperty("color") Optional<String> color,
        @JsonProperty("description") Optional<String> description,
        @JsonProperty("readOnly") Optional<Boolean> readOnly) {
        this(validateDisplayName(displayName), color.map(UpdateLabelRequest::asColor), description, readOnly);
    }

    private static DisplayName validateDisplayName(String value) {
        if (value == null || value.isBlank()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("'displayName' is required and must not be empty")
                .haltError();
        }
        return new DisplayName(value);
    }

    private static Color asColor(String value) {
        scala.util.Either<IllegalArgumentException, Color> result = Color.validate(value);
        if (result.isLeft()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(result.left().get().getMessage())
                .haltError();
        }
        return result.toOption().get();
    }
}
