package com.linagora.tmail.contact;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.util.streams.Iterators;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;


public class JCardObjectDeserializer extends StdDeserializer<JCardObject> {
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
            throw new RuntimeException("The FN field is required according to specification.");
        }

        return new JCardObject(jCardProperties.get(FN), getOptionalFromMap(jCardProperties, EMAIL));
    }

    private static Map<String, String> collectJCardProperties(Iterator<JsonNode> propertiesIterator) {
        return Iterators.toStream(propertiesIterator)
            .map(JCardObjectDeserializer::getPropertyKeyValuePair)
            .filter(pair -> pair != ImmutablePair.<String, String>nullPair())
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private static ImmutablePair<String, String> getPropertyKeyValuePair(JsonNode propertyNode) {
        String propertyName = propertyNode.get(PROPERTY_NAME_INDEX).asText();
        if (SUPPORTED_PROPERTY_NAMES.contains(propertyName)) {
            String propertyValue = propertyNode.get(TEXT_PROPERTY_VALUE_INDEX).asText();
            return ImmutablePair.of(propertyName, propertyValue);
        } else {
            return ImmutablePair.nullPair();
        }
    }

    private Optional<String> getOptionalFromMap(Map<String, String> map, String key) {
        return Optional.ofNullable(map.getOrDefault(key, null));
    }
}
