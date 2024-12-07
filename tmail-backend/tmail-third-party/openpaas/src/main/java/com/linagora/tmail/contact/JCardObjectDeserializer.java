package com.linagora.tmail.contact;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.mail.internet.AddressException;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.james.util.streams.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;


public class JCardObjectDeserializer extends StdDeserializer<JCardObject> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JCardObjectDeserializer.class);

    private static final String FN = "fn";
    private static final String EMAIL = "email";
    private static final Set<String> SUPPORTED_PROPERTY_NAMES = Set.of(FN, EMAIL);
    private static final int PROPERTY_NAME_INDEX = 0;
    private static final int PROPERTIES_ARRAY_INDEX = 1;
    private static final int TEXT_PROPERTY_VALUE_INDEX = 3;

    public JCardObjectDeserializer() {
        this(null);
    }

    protected JCardObjectDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public JCardObject deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        JsonNode jCardPropertiesArray = node.get(PROPERTIES_ARRAY_INDEX);
        Map<String, String> jCardProperties =
            collectJCardProperties(jCardPropertiesArray.iterator());

        if (!jCardProperties.containsKey(FN)) {
            String json = node.toString();
            LOGGER.warn("""
                Missing 'fn' property in the provided JCard object. 'fn' is required according to the specifications.
                Received data: {}.
                Ensure the 'fn' property is present and correctly formatted.""", json);
        }

        Optional<MailAddress> maybeMailAddress = getOptionalFromMap(jCardProperties, EMAIL)
            .flatMap(email -> {
                try {
                    return Optional.of(new MailAddress(email));
                } catch (AddressException e) {
                    LOGGER.info("Invalid contact mail address '{}' found in JCard Object", email);
                    return Optional.empty();
                }
            });

        return new JCardObject(getOptionalFromMap(jCardProperties, FN), maybeMailAddress);
    }

    private static Map<String, String> collectJCardProperties(Iterator<JsonNode> propertiesIterator) {
        return Iterators.toStream(propertiesIterator)
            .map(JCardObjectDeserializer::getPropertyKeyValuePair)
            .flatMap(Optional::stream)
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private static Optional<ImmutablePair<String, String>> getPropertyKeyValuePair(JsonNode propertyNode) {
        String propertyName = propertyNode.get(PROPERTY_NAME_INDEX).asText();
        if (SUPPORTED_PROPERTY_NAMES.contains(propertyName)) {
            String propertyValue = propertyNode.get(TEXT_PROPERTY_VALUE_INDEX).asText();
            return Optional.of(ImmutablePair.of(propertyName, propertyValue));
        } else {
            return Optional.empty();
        }
    }

    private Optional<String> getOptionalFromMap(Map<String, String> map, String key) {
        return Optional.ofNullable(map.getOrDefault(key, null));
    }
}
