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

package com.linagora.tmail.contact;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.mail.internet.AddressException;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.james.core.MailAddress;
import org.apache.james.util.streams.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;


public class JCardObjectDeserializer extends StdDeserializer<JCardObject> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JCardObjectDeserializer.class);

    private static final String FN = "fn";
    private static final String EMAIL = "email";
    private static final Set<String> SUPPORTED_PROPERTY_NAMES = Set.of(FN, EMAIL);
    private static final int PROPERTY_NAME_INDEX = 0;
    private static final int PROPERTIES_ARRAY_INDEX = 1;
    private static final int TEXT_PROPERTY_VALUE_INDEX = 3;
    private static final String MAILTO_PREFIX = "mailto:";

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
        Multimap<String, String> jCardProperties =
            collectJCardProperties(jCardPropertiesArray.iterator());

        if (!jCardProperties.containsKey(FN)) {
            String json = node.toString();
            LOGGER.warn("""
                Missing 'fn' property in the provided JCard object. 'fn' is required according to the specifications.
                Received data: {}.
                Ensure the 'fn' property is present and correctly formatted.""", json);
        }

        List<MailAddress> mailAddresses = jCardProperties.get(EMAIL)
            .stream()
            .map(sanitizeMailToPrefixIfNeeded())
            .flatMap(email -> {
                try {
                    return Stream.of(new MailAddress(email));
                } catch (AddressException e) {
                    LOGGER.info("Invalid contact mail address '{}' found in JCard Object", email);
                    return Stream.empty();
                }
            })
            .toList();

        return new JCardObject(getFormattedName(jCardProperties), mailAddresses);
    }

    private Function<String, String> sanitizeMailToPrefixIfNeeded() {
        return email -> email.startsWith(MAILTO_PREFIX) ? email.substring(MAILTO_PREFIX.length()) : email;
    }

    private static Multimap<String, String> collectJCardProperties(Iterator<JsonNode> propertiesIterator) {
        Multimap<String, String> multimap = ArrayListMultimap.create();
        Iterators.toStream(propertiesIterator)
            .map(JCardObjectDeserializer::getPropertyKeyValuePair)
            .flatMap(Optional::stream)
            .forEach(pair -> multimap.put(pair.getKey(), pair.getValue()));
        return multimap;
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

    private Optional<String> getFormattedName(Multimap<String, String> multimap) {
        return multimap.get(FN)
            .stream()
            .findFirst();
    }
}
