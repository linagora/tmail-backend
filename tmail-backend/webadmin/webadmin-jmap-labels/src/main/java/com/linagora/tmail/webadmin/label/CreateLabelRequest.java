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

import org.apache.james.jmap.mail.Keyword;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.linagora.tmail.james.jmap.model.Color;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelCreationRequest;
import com.linagora.tmail.james.jmap.model.LabelId;

import scala.Option;
import scala.compat.java8.OptionConverters;

public record CreateLabelRequest(String displayName, String color, Optional<String> description, Optional<String> keyword) {
    @JsonCreator
    public CreateLabelRequest(@JsonProperty("displayName") String displayName,
                              @JsonProperty("color") String color,
                              @JsonProperty("description") Optional<String> description,
                              @JsonProperty("keyword") Optional<String> keyword) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
        this.keyword = keyword;
    }

    private DisplayName validateDisplayName() {
        if (displayName == null || displayName.isBlank()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("'displayName' is required and must not be empty")
                .haltError();
        }
        return new DisplayName(displayName);
    }

    private Option<Color> toScalaColor() {
        if (color == null) {
            return scalaNone();
        }
        scala.util.Either<IllegalArgumentException, Color> result = Color.validate(color);
        if (result.isLeft()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(result.left().get().getMessage())
                .haltError();
        }
        return scalaSome(result.toOption().get());
    }

    private Option<String> toScalaDescription() {
        return OptionConverters.toScala(description);
    }

    public Optional<String> validatedKeyword() {
        return keyword
            .map(value -> {
                if (!Keyword.of(value).isSuccess()) {
                    throw ErrorResponder.builder()
                        .statusCode(HttpStatus.BAD_REQUEST_400)
                        .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                        .message("Invalid keyword '%s'", value)
                        .haltError();
                }
                return value;
            });
    }

    public Label asLabel() {
        Preconditions.checkState(keyword.isPresent());
        String validatedKeyword = this.keyword.get();

        return new Label(
            toLabelId(validatedKeyword),
            validateDisplayName(),
            validatedKeyword,
            toScalaColor(),
            toScalaDescription());
    }

    private LabelId toLabelId(String validatedKeyword) {
        try {
            return LabelId.fromKeyword(validatedKeyword);
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid keyword '%s': must also be a valid JMAP id (alphanumeric and hyphens only)", validatedKeyword)
                .haltError();
        }
    }

    public LabelCreationRequest toCreationRequest() {
        return new LabelCreationRequest(validateDisplayName(), toScalaColor(), toScalaDescription());
    }

    @SuppressWarnings("unchecked")
    private static <T> Option<T> scalaSome(T value) {
        return OptionConverters.toScala(Optional.of(value));
    }

    @SuppressWarnings("unchecked")
    private static <T> Option<T> scalaNone() {
        return OptionConverters.toScala(Optional.empty());
    }
}
