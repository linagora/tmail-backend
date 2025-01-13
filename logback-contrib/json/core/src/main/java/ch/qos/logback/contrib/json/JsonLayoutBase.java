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
package ch.qos.logback.contrib.json;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.LayoutBase;

/**
 * @author Les Hazlewood
 * @author Pierre Queinnec
 * @author Espen A. Fossen
 * @since 0.1
 */
public abstract class JsonLayoutBase<E> extends LayoutBase<E> {

    public static final String CONTENT_TYPE = "application/json";

    protected boolean includeTimestamp;
    protected String timestampFormat;
    protected String timestampFormatTimezoneId;
    protected boolean appendLineSeparator;

    protected JsonFormatter jsonFormatter;

    private DateTimeFormatter dateTimeFormatter;

    public JsonLayoutBase() {
        this.includeTimestamp = true;
        this.appendLineSeparator = false;
        updateDateTimeFormatter();
    }

    private void updateDateTimeFormatter() {
        if (this.timestampFormat != null) {
            if (this.timestampFormatTimezoneId != null) {
                this.dateTimeFormatter = DateTimeFormatter.ofPattern(this.timestampFormat)
                        .withZone(ZoneId.of(this.timestampFormatTimezoneId));
            } else {
                this.dateTimeFormatter = DateTimeFormatter.ofPattern(this.timestampFormat)
                        .withZone(ZoneId.systemDefault());
            }
        }
    }

    @Override
    public String doLayout(E event) {
        Map map = toJsonMap(event);
        if (map == null || map.isEmpty()) {
            return null;
        }
        String result = getStringFromFormatter(map);
        return isAppendLineSeparator() ? result + CoreConstants.LINE_SEPARATOR : result;
    }

    private String getStringFromFormatter(Map map) {
        JsonFormatter formatter = getJsonFormatter();
        if (formatter == null) {
            addError("JsonFormatter has not been configured on JsonLayout instance " + getClass().getName() + ".  Defaulting to map.toString().");
            return map.toString();
        }

        try {
            return formatter.toJsonString(map);
        } catch (Exception e) {
            addError("JsonFormatter failed.  Defaulting to map.toString().  Message: " + e.getMessage(), e);
            return map.toString();
        }
    }

    protected String formatTimestamp(long timestamp) {
        if (this.timestampFormat == null || timestamp < 0) {
            return String.valueOf(timestamp);
        }
        Instant instant = Instant.ofEpochMilli(timestamp);

        return dateTimeFormatter.format(instant);
    }

    public void addMap(String key, boolean field, Map<String, ?> mapValue, Map<String, Object> map) {
        if (field && mapValue != null && !mapValue.isEmpty()) {
            map.put(key, mapValue);
        }
    }

    public void addTimestamp(String key, boolean field, long timeStamp, Map<String, Object> map) {
        if (field) {
            String formatted = formatTimestamp(timeStamp);
            if (formatted != null) {
                map.put(key, formatted);
            }
        }
    }

    public void add(String fieldName, boolean field, String value, Map<String, Object> map) {
        if (field && value != null) {
            map.put(fieldName, value);
        }
    }

    protected abstract Map toJsonMap(E e);

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    public boolean isIncludeTimestamp() {
        return includeTimestamp;
    }

    public void setIncludeTimestamp(boolean includeTimestamp) {
        this.includeTimestamp = includeTimestamp;
    }

    public JsonFormatter getJsonFormatter() {
        return jsonFormatter;
    }

    public void setJsonFormatter(JsonFormatter jsonFormatter) {
        this.jsonFormatter = jsonFormatter;
    }

    public String getTimestampFormat() {
        return timestampFormat;
    }

    public void setTimestampFormat(String timestampFormat) {
        this.timestampFormat = timestampFormat;
        updateDateTimeFormatter();
    }

    public String getTimestampFormatTimezoneId() {
        return timestampFormatTimezoneId;
    }

    public void setTimestampFormatTimezoneId(String timestampFormatTimezoneId) {
        this.timestampFormatTimezoneId = timestampFormatTimezoneId;
        updateDateTimeFormatter();
    }

    public boolean isAppendLineSeparator() {
        return appendLineSeparator;
    }

    public void setAppendLineSeparator(boolean appendLineSeparator) {
        this.appendLineSeparator = appendLineSeparator;
    }
}
