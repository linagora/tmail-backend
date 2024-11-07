package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.apache.mailet.AttributeName;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.Exchange;
import com.rabbitmq.client.BuiltinExchangeType;

public class OpenPaasAmqpForwardAttributeConfigTest {

    @Test
    void shouldThrowIfAnyFieldIsNull() {
        assertThatThrownBy(() -> new OpenPaasAmqpForwardAttributeConfig(
            AttributeName.of("attribute"),
            Exchange.of("exchange"),
            BuiltinExchangeType.DIRECT,
            null
        ));

        assertThatThrownBy(() -> new OpenPaasAmqpForwardAttributeConfig(
            AttributeName.of("attribute"),
            Exchange.of("exchange"),
            null,
            ""
        ));

        assertThatThrownBy(() -> new OpenPaasAmqpForwardAttributeConfig(
            AttributeName.of("attribute"),
            null,
            BuiltinExchangeType.DIRECT,
            ""
        ));

        assertThatThrownBy(() -> new OpenPaasAmqpForwardAttributeConfig(
            null,
            Exchange.of("exchange"),
            BuiltinExchangeType.DIRECT,
            ""
        ));
    }

    @Test
    void fromShouldThrowIfExchangeIsNotSet() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME, "attribute")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME, "direct")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ROUTING_KEY_PARAMETER_NAME, "routingKey")
            .build();

        assertThatThrownBy(() -> OpenPaasAmqpForwardAttributeConfig.from(mailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void fromShouldNotThrowIfExchangeIsBlank() throws MailetException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME, "attribute")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME, "direct")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ROUTING_KEY_PARAMETER_NAME, "routingKey")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_PARAMETER_NAME, "")
            .build();

        OpenPaasAmqpForwardAttributeConfig subject =
            OpenPaasAmqpForwardAttributeConfig.from(mailetConfig);

        assertThat(subject.exchange().isDefaultExchange()).isTrue();
    }

    @Test
    void fromShouldNotThrowIfExchangeIsValid() throws MailetException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME, "attribute")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME, "direct")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ROUTING_KEY_PARAMETER_NAME, "routingKey")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_PARAMETER_NAME, "test-exchange_123.name:example")
            .build();

        OpenPaasAmqpForwardAttributeConfig.from(mailetConfig);
    }

    @Test
    void fromShouldThrowIfAttributeIsBlank() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME, "")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME, "direct")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ROUTING_KEY_PARAMETER_NAME, "routingKey")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_PARAMETER_NAME, "test-exchange_123.name:example")
            .build();

        assertThatThrownBy(() -> OpenPaasAmqpForwardAttributeConfig.from(mailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void fromShouldThrowIfExchangeTypeIsUnknown() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME, "attribute")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME, "unknown")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ROUTING_KEY_PARAMETER_NAME, "routingKey")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_PARAMETER_NAME, "test-exchange_123.name:example")
            .build();

        assertThatThrownBy(() -> OpenPaasAmqpForwardAttributeConfig.from(mailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void fromShouldUseDefaultExchangeTypeWhenItIsBlank() throws MailetException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME, "attribute")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME, "")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ROUTING_KEY_PARAMETER_NAME, "routingKey")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_PARAMETER_NAME, "test-exchange_123.name:example")
            .build();

        OpenPaasAmqpForwardAttributeConfig subject =
            OpenPaasAmqpForwardAttributeConfig.from(mailetConfig);

        assertThat(subject.exchangeType()).isEqualTo(OpenPaasAmqpForwardAttributeConfig.DEFAULT_EXCHANGE_TYPE);
    }

    @Test
    void fromShouldUseDefaultRoutingKeyIsNotSet() throws MailetException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(OpenPaasAmqpForwardAttributeConfig.ATTRIBUTE_PARAMETER_NAME, "attribute")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_TYPE_PARAMETER_NAME, "direct")
            .setProperty(OpenPaasAmqpForwardAttributeConfig.EXCHANGE_PARAMETER_NAME, "test-exchange_123.name:example")
            .build();

        OpenPaasAmqpForwardAttributeConfig subject =
            OpenPaasAmqpForwardAttributeConfig.from(mailetConfig);

        assertThat(subject.routingKey()).isEqualTo(OpenPaasAmqpForwardAttributeConfig.ROUTING_KEY_DEFAULT_VALUE);
    }

}
