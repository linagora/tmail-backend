package com.linagora.tmail.james.jmap.contact;

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.base.Strings;

public class jCardObjectDeserializer extends StdDeserializer<jCardObject> {
    private static final String FN = "fn";
    private static final String EMAIL = "email";
    private static final int PROPERTY_NAME_INDEX = 0;
    private static final int PROPERTIES_ARRAY_INDEX = 1;
    private static final int TEXT_PROPERTY_VALUE_INDEX = 3;

    public jCardObjectDeserializer() {
        this(null);
    }

    protected jCardObjectDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public jCardObject deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JacksonException {
        JsonNode node = p.getCodec().readTree(p);

        JsonNode jCardPropertiesArray = node.get(PROPERTIES_ARRAY_INDEX);
        Iterator<JsonNode> propertiesIterator = jCardPropertiesArray.iterator();

        String fn = "";
        String email = "";
        while (propertiesIterator.hasNext()) {
            JsonNode propertyNode = propertiesIterator.next();
            String propertyName = propertyNode.get(PROPERTY_NAME_INDEX).asText();

            if (FN.equals(propertyName)) {
                fn = propertyNode.get(TEXT_PROPERTY_VALUE_INDEX).asText();
            } else if (EMAIL.equals(propertyName)) {
                email = propertyNode.get(TEXT_PROPERTY_VALUE_INDEX).asText();
            }
        }

        if (Strings.isNullOrEmpty(fn)) {
            throw new RuntimeException("The fn property is required for a valid jCard object.");
        } else if (Strings.isNullOrEmpty(email)) {
            throw new RuntimeException("The email property is required for a valid jCard object.");
        }

        return new jCardObject(fn, email);
    }
}
