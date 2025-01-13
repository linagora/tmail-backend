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

package com.linagora.tmail.mailet;

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.mail.MessagingException;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.OpenPaasModule;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.ShutdownSignalException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;


/**
 * This mailet forwards the attributes values to a OpenPaas through the AMQP protocol.
 * <br />
 * It takes 3 parameters:
 * <ul>
 * <li>attribute (mandatory): content to be forwarded, expected to be a Map&lt;String, byte[]&gt;
 * where the byte[] content is issued from a MimeBodyPart.
 * It is typically generated from the StripAttachment mailet.</li>
 * <li>exchange (mandatory): name of the AMQP exchange.</li>
 * <li>exchange_type (optional, default to "direct"): type of the exchange. Valid values are: direct, fanout, topic, headers.</li>
 * <li>routing_key (optional, default to empty string): name of the routing key on this exchange.</li>
 * </ul>
 *
 * This mailet will send the data attached to the mail as an attribute holding a map.
 * <p>
 * @see OpenPaasAmqpForwardAttributeConfig
 */
public class OpenPaasAmqpForwardAttribute extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasAmqpForwardAttribute.class);

    private final Sender sender;
    private OpenPaasAmqpForwardAttributeConfig config;

    @Inject
    public OpenPaasAmqpForwardAttribute(OpenPaasRabbitMQChannelPoolHolder openPaasRabbitMQChannelPoolHolder)
        throws MailetException {
        ReactorRabbitMQChannelPool openPaasRabbitChannelPool =
            openPaasRabbitMQChannelPoolHolder.get().orElseThrow(() ->
                new MailetException(
                    "Failed to initialize mailet. OpenPaasModule is required to this mailet to function correctly."));
        openPaasRabbitChannelPool.start();

        sender = openPaasRabbitChannelPool.getSender();
    }

    @Override
    public void init(MailetConfig newConfig) throws MessagingException {
        super.init(newConfig);
        config = OpenPaasAmqpForwardAttributeConfig.from(newConfig);

        ExchangeSpecification exchangeSpecification =
            ExchangeSpecification.exchange(config.exchange().name())
                .durable(DURABLE)
                .type(config.exchangeType().getType());

        sender.declareExchange(exchangeSpecification)
            .onErrorResume(error -> error instanceof ShutdownSignalException && error.getMessage().contains("reply-code=406, reply-text=PRECONDITION_FAILED"),
                error -> {
                    LOGGER.warn("Exchange `{}` already exists but with different configuration. Ignoring this error. \nError message: {}", config.exchange(), error.getMessage());
                    return Mono.empty();
                })
            .block();
    }

    @Override
    public void service(Mail mail) throws MailetException {
        mail.getAttribute(config.attribute())
            .map(Throwing.function(this::getAttributeContent).sneakyThrow())
            .ifPresent(this::sendContent);
    }

    private Stream<byte[]> getAttributeContent(Attribute attribute) throws MailetException {
        return extractAttributeValueContent(attribute.getValue().value())
            .orElseThrow(() -> new MailetException("Invalid attribute found into attribute "
                                                   + config.attribute().asString() + "class Map or List or String expected but "
                                                   + attribute + " found."));
    }

    @SuppressWarnings("unchecked")
    private Optional<Stream<byte[]>> extractAttributeValueContent(Object attributeContent) {
        if (attributeContent instanceof Map) {
            return Optional.of(((Map<String, AttributeValue<byte[]>>) attributeContent).values().stream()
                .map(AttributeValue::getValue));
        }
        if (attributeContent instanceof List) {
            return Optional.of(((List<AttributeValue<byte[]>>) attributeContent).stream().map(AttributeValue::value));
        }
        if (attributeContent instanceof String) {
            return Optional.of(Stream.of(((String) attributeContent).getBytes(StandardCharsets.UTF_8)));
        }
        return Optional.empty();
    }

    private void sendContent(Stream<byte[]> content) {
        try {
            sender.send(Flux.fromStream(content)
                    .map(bytes -> new OutboundMessage(config.exchange().name(), config.routingKey(), bytes)))
                .block();
        } catch (AlreadyClosedException e) {
            LOGGER.error("AlreadyClosedException while writing to AMQP", e);
        } catch (Exception e) {
            LOGGER.error("IOException while writing to AMQP", e);
        }
    }

    @PreDestroy
    public void cleanUp() {
        sender.close();
    }

    @Override
    public String getMailetInfo() {
        return "OpenPaasAmqpForwardAttribute";
    }

    public static class OpenPaasRabbitMQChannelPoolHolder {

        @com.google.inject.Inject(optional = true)
        @Named(OpenPaasModule.OPENPAAS_INJECTION_KEY)
        ReactorRabbitMQChannelPool openPaasRabbitChannelPool;

        public Optional<ReactorRabbitMQChannelPool> get() {
            return Optional.ofNullable(openPaasRabbitChannelPool);
        }
    }
 }
