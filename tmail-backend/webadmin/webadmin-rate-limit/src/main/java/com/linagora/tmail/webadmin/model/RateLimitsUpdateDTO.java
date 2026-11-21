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


package com.linagora.tmail.webadmin.model;

import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.webadmin.utils.JsonExtractException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.google.common.base.Preconditions;
import com.linagora.tmail.rate.limiter.api.model.LimitUpdate;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

/**
 * Omitted mail limits are unset. Recipient limits were introduced later: omitting them leaves the stored
 * value untouched so that clients unaware of them do not erase them. An explicit null unsets them.
 */
public record RateLimitsUpdateDTO(Optional<Long> mailsSentPerMinute,
                                  Optional<Long> mailsSentPerHours,
                                  Optional<Long> mailsSentPerDays,
                                  Optional<Long> mailsReceivedPerMinute,
                                  Optional<Long> mailsReceivedPerHours,
                                  Optional<Long> mailsReceivedPerDays,
                                  LimitUpdate recipientsSentPerMinute,
                                  LimitUpdate recipientsSentPerHours,
                                  LimitUpdate recipientsSentPerDays) {
    private static final long UNLIMITED = -1L;

    @JsonCreator
    public static RateLimitsUpdateDTO fromJson(@JsonProperty("mailsSentPerMinute") JsonNode mailsSentPerMinute,
                                               @JsonProperty("mailsSentPerHours") JsonNode mailsSentPerHours,
                                               @JsonProperty("mailsSentPerDays") JsonNode mailsSentPerDays,
                                               @JsonProperty("mailsReceivedPerMinute") JsonNode mailsReceivedPerMinute,
                                               @JsonProperty("mailsReceivedPerHours") JsonNode mailsReceivedPerHours,
                                               @JsonProperty("mailsReceivedPerDays") JsonNode mailsReceivedPerDays,
                                               @JsonProperty("recipientsSentPerMinute") JsonNode recipientsSentPerMinute,
                                               @JsonProperty("recipientsSentPerHours") JsonNode recipientsSentPerHours,
                                               @JsonProperty("recipientsSentPerDays") JsonNode recipientsSentPerDays) {
        return new RateLimitsUpdateDTO(
            asLimit("mailsSentPerMinute", mailsSentPerMinute),
            asLimit("mailsSentPerHours", mailsSentPerHours),
            asLimit("mailsSentPerDays", mailsSentPerDays),
            asLimit("mailsReceivedPerMinute", mailsReceivedPerMinute),
            asLimit("mailsReceivedPerHours", mailsReceivedPerHours),
            asLimit("mailsReceivedPerDays", mailsReceivedPerDays),
            asLimitUpdate("recipientsSentPerMinute", recipientsSentPerMinute),
            asLimitUpdate("recipientsSentPerHours", recipientsSentPerHours),
            asLimitUpdate("recipientsSentPerDays", recipientsSentPerDays));
    }

    /**
     * Describes a payload error without the Jackson internals (class names, source location).
     */
    public static String describe(JsonExtractException e) {
        Throwable cause = e.getCause();
        if (cause instanceof UnrecognizedPropertyException unrecognized) {
            return String.format("Unknown field '%s'", unrecognized.getPropertyName());
        }
        if (cause instanceof JsonMappingException mappingException && mappingException.getCause() instanceof IllegalArgumentException validationError) {
            return validationError.getMessage();
        }
        if (cause instanceof JsonMappingException mappingException && !mappingException.getPath().isEmpty()) {
            return String.format("Invalid field '%s': %s", fieldPath(mappingException), mappingException.getOriginalMessage());
        }
        if (cause instanceof JsonProcessingException processingException) {
            return processingException.getOriginalMessage();
        }
        return e.getMessage();
    }

    private static String fieldPath(JsonMappingException e) {
        return e.getPath().stream()
            .map(reference -> Optional.ofNullable(reference.getFieldName()).orElse(String.valueOf(reference.getIndex())))
            .collect(Collectors.joining("."));
    }

    private static Optional<Long> asLimit(String field, JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        return Optional.of(validatedLimit(field, node));
    }

    private static LimitUpdate asLimitUpdate(String field, JsonNode node) {
        if (node == null) {
            return LimitUpdate.KEEP;
        }
        if (node.isNull()) {
            return LimitUpdate.clear();
        }
        return LimitUpdate.replace(validatedLimit(field, node));
    }

    private static long validatedLimit(String field, JsonNode node) {
        Preconditions.checkArgument(node.isNumber() && node.canConvertToExactIntegral() && node.canConvertToLong(), "%s must be an integer", field);
        long value = node.asLong();
        Preconditions.checkArgument(value >= UNLIMITED, "%s must be -1 (unlimited) or a positive or zero integer", field);
        return value;
    }

    public RateLimitingDefinition toRateLimitingDefinition(RateLimitingDefinition current) {
        return RateLimitingDefinition.builder()
            .mailsSentPerMinute(mailsSentPerMinute.orElse(null))
            .mailsSentPerHours(mailsSentPerHours.orElse(null))
            .mailsSentPerDays(mailsSentPerDays.orElse(null))
            .mailsReceivedPerMinute(mailsReceivedPerMinute.orElse(null))
            .mailsReceivedPerHours(mailsReceivedPerHours.orElse(null))
            .mailsReceivedPerDays(mailsReceivedPerDays.orElse(null))
            .recipientsSentPerMinute(recipientsSentPerMinute.applyTo(current.recipientsSentPerMinute()).orElse(null))
            .recipientsSentPerHours(recipientsSentPerHours.applyTo(current.recipientsSentPerHours()).orElse(null))
            .recipientsSentPerDays(recipientsSentPerDays.applyTo(current.recipientsSentPerDays()).orElse(null))
            .build();
    }
}
