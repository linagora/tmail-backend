/**
 * Copyright (C) 2024, LINAGORA. All rights reserved.
 *
 * This file is licensed under the terms of the
 * Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 * 
 * This work is based on the work found at
 * https://github.com/qos-ch/logback-contrib
 * authored by the logback-contrib developers.
 */
package ch.qos.logback.contrib.jackson;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.contrib.json.JsonFormatter;

/**
 * Jackson-specific implementation of the {@link JsonFormatter}.
 *
 * @author Les Hazlewood
 * @since 0.1
 */
public class JacksonJsonFormatter implements JsonFormatter {

    public static final int BUFFER_SIZE = 512;

    private ObjectMapper objectMapper;
    private boolean prettyPrint;

    public JacksonJsonFormatter() {
        this.objectMapper = new ObjectMapper();
        this.prettyPrint = false;
    }

    @Override
    public String toJsonString(Map m) throws IOException {
        StringWriter writer = new StringWriter(BUFFER_SIZE);
        JsonGenerator generator = this.objectMapper.getFactory().createJsonGenerator(writer);

        if (isPrettyPrint()) {
            generator.useDefaultPrettyPrinter();
        }

        this.objectMapper.writeValue(generator, m);

        writer.flush();

        return writer.toString();
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }
}

