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

import java.util.Map;

/**
 * A {@code JsonFormatter} formats a data {@link Map Map} into a JSON string.
 *
 * @author Les Hazlewood
 * @since 0.1
 */
public interface JsonFormatter {

    /**
     * Converts the specified map into a JSON string.
     *
     * @param m the map to be converted.
     * @return a JSON String representation of the specified Map instance.
     * @throws Exception if there is a problem converting the map to a String.
     */
    String toJsonString(Map m) throws Exception;
}
