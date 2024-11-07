package com.linagora.tmail.mailet;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.mailet.AttributeName;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetException;

import com.google.common.base.Preconditions;
import com.linagora.tmail.Exchange;
import com.rabbitmq.client.BuiltinExchangeType;

public record OpenPaasAmqpForwardAttributeConfig(
    AttributeName attribute,
    Exchange exchange,
    BuiltinExchangeType exchangeType,
    String routingKey) {
    protected static final BuiltinExchangeType DEFAULT_EXCHANGE_TYPE = BuiltinExchangeType.DIRECT;
    protected static final String ROUTING_KEY_DEFAULT_VALUE = "";

    public static final String EXCHANGE_PARAMETER_NAME = "exchange";
    public static final String EXCHANGE_TYPE_PARAMETER_NAME = "exchange_type";
    public static final String ROUTING_KEY_PARAMETER_NAME = "routing_key";
    public static final String ATTRIBUTE_PARAMETER_NAME = "attribute";
    public static final List<String> VALID_EXCHANGE_TYPES = List.of("direct", "fanout", "topic", "headers");

    public OpenPaasAmqpForwardAttributeConfig {
        Preconditions.checkNotNull(attribute);
        Preconditions.checkNotNull(exchange);
        Preconditions.checkNotNull(exchangeType);
        Preconditions.checkNotNull(routingKey);
    }

    public static OpenPaasAmqpForwardAttributeConfig from(MailetConfig mailetConfig)
        throws MailetException {
        AttributeName attribute = readAttributeParameter(mailetConfig);
        Exchange exchange = readExchangeParameter(mailetConfig);
        BuiltinExchangeType exchangeType = readExchangeTypeParameter(mailetConfig).orElse(DEFAULT_EXCHANGE_TYPE);
        String routingKey = readRoutingKeyParameter(mailetConfig).orElse(ROUTING_KEY_DEFAULT_VALUE);

        return new OpenPaasAmqpForwardAttributeConfig(attribute, exchange, exchangeType, routingKey);
    }

    private static Exchange readExchangeParameter(MailetConfig mailetConfig) throws MailetException {
        String exchange = mailetConfig.getInitParameter(EXCHANGE_PARAMETER_NAME);
        if (exchange == null) {
            throw new MailetException("No value for " + EXCHANGE_PARAMETER_NAME + " parameter was provided.");
        }
        return Exchange.of(exchange);
    }

    private static Optional<BuiltinExchangeType> readExchangeTypeParameter(MailetConfig mailetConfig) throws MailetException {
        String exchangeType = mailetConfig.getInitParameter(EXCHANGE_TYPE_PARAMETER_NAME);
        if (StringUtils.isNotBlank(exchangeType) && !VALID_EXCHANGE_TYPES.contains(exchangeType)) {
            throw new MailetException("Invalid value for " + EXCHANGE_TYPE_PARAMETER_NAME + " parameter was provided: " + exchangeType + ". Valid values are: " +
                                      VALID_EXCHANGE_TYPES);
        }

        return Optional.ofNullable(exchangeType)
            .filter(StringUtils::isNotBlank)
            .map(String::toUpperCase)
            .map(BuiltinExchangeType::valueOf);
    }

    private static Optional<String> readRoutingKeyParameter(MailetConfig mailetConfig) {
        return Optional.ofNullable(mailetConfig.getInitParameter(ROUTING_KEY_PARAMETER_NAME));
    }

    private static AttributeName readAttributeParameter(MailetConfig mailetConfig) throws MailetException {
        String rawAttribute = mailetConfig.getInitParameter(ATTRIBUTE_PARAMETER_NAME);
        if (StringUtils.isBlank(rawAttribute)) {
            throw new MailetException("No value for " + ATTRIBUTE_PARAMETER_NAME + " parameter was provided.");
        }
        return AttributeName.of(rawAttribute);
    }
}
