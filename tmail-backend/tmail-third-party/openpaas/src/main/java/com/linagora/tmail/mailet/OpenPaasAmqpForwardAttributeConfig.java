package com.linagora.tmail.mailet;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.mailet.AttributeName;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetException;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.rabbitmq.client.BuiltinExchangeType;

public record OpenPaasAmqpForwardAttributeConfig(
    AttributeName attribute,
    String exchange,
    Optional<BuiltinExchangeType> maybeExchangeType,
    String routingKey) {

    public static final String EXCHANGE_PARAMETER_NAME = "exchange";
    public static final String EXCHANGE_TYPE_PARAMETER_NAME = "exchange_type";
    public static final String ROUTING_KEY_PARAMETER_NAME = "routing_key";
    public static final String ATTRIBUTE_PARAMETER_NAME = "attribute";
    public static final String ROUTING_KEY_DEFAULT_VALUE = "";
    public static final List<String> VALIDATE_EXCHANGE_TYPES = List.of("direct", "fanout", "topic", "headers");

    public OpenPaasAmqpForwardAttributeConfig {
        Preconditions.checkNotNull(attribute);
        Preconditions.checkNotNull(exchange);
        Preconditions.checkNotNull(maybeExchangeType);
        Preconditions.checkNotNull(routingKey);

        Preconditions.checkArgument(!exchange.isBlank());
    }

    public static OpenPaasAmqpForwardAttributeConfig from(MailetConfig mailetConfig)
        throws MailetException {
        AttributeName attribute = getAttributeParameter(mailetConfig);
        String exchange = getExchangeParameter(mailetConfig);
        Optional<BuiltinExchangeType> maybeExchangeType = getExchangeTypeParameter(mailetConfig);
        String routingKey = getRoutingKeyParameter(mailetConfig);

        return new OpenPaasAmqpForwardAttributeConfig(
            attribute,
            exchange,
            maybeExchangeType,
            routingKey);
    }

    private static String getExchangeParameter(MailetConfig mailetConfig) throws MailetException {
        String exchange = mailetConfig.getInitParameter(EXCHANGE_PARAMETER_NAME);
        if (StringUtils.isBlank(exchange)) {
            throw new MailetException("No value for " + EXCHANGE_PARAMETER_NAME + " parameter was provided.");
        }
        return exchange;
    }

    private static Optional<BuiltinExchangeType> getExchangeTypeParameter(MailetConfig mailetConfig) throws MailetException {
        String exchangeType = mailetConfig.getInitParameter(EXCHANGE_TYPE_PARAMETER_NAME);
        if (StringUtils.isNotEmpty(exchangeType) && !VALIDATE_EXCHANGE_TYPES.contains(exchangeType)) {
            throw new MailetException("Invalid value for " + EXCHANGE_TYPE_PARAMETER_NAME + " parameter was provided: " + exchangeType + ". Valid values are: " + VALIDATE_EXCHANGE_TYPES);
        }

        return Optional.ofNullable(exchangeType)
            .filter(StringUtils::isNotBlank)
            .map(String::toUpperCase)
            .map(BuiltinExchangeType::valueOf);
    }

    private static String getRoutingKeyParameter(MailetConfig mailetConfig) {
        return Optional.ofNullable(mailetConfig.getInitParameter(ROUTING_KEY_PARAMETER_NAME))
            .orElse(ROUTING_KEY_DEFAULT_VALUE);
    }

    private static AttributeName getAttributeParameter(MailetConfig mailetConfig) throws MailetException {
        String rawAttribute = mailetConfig.getInitParameter(ATTRIBUTE_PARAMETER_NAME);
        if (Strings.isNullOrEmpty(rawAttribute)) {
            throw new MailetException("No value for " + ATTRIBUTE_PARAMETER_NAME + " parameter was provided.");
        }
        return AttributeName.of(rawAttribute);
    }
}
