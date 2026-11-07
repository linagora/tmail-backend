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
 *******************************************************************/

package com.linagora.tmail.james.jmap.event;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.SearchResultEntry;

/**
 * The LDAP attributes that can be read to provision identities and interpolate {@code {ldap:<name>}} placeholders
 * of signature templates: first name, surname, username and the configured {@code extraAttributes} of the
 * {@link IdentityProvisionListener}.
 *
 * Credentials ({@code userPassword}) and the {@code *} / {@code +} wildcards are never part of them, even when
 * configured: signatures are readable by the user, by administrators, and appended to outgoing mails.
 */
public record LdapSignatureAttributes(String firstnameAttribute, String surnameAttribute, String usernameAttribute,
                                      Set<String> requestedAttributes) {
    // "*" and "+" would request every user, respectively operational, attribute
    private static final Set<String> FORBIDDEN_ATTRIBUTES = ImmutableSet.of("userpassword", "*", "+");

    public static LdapSignatureAttributes from(LdapRepositoryConfiguration ldapConfiguration) {
        return from(ldapConfiguration, new BaseHierarchicalConfiguration());
    }

    public static LdapSignatureAttributes from(LdapRepositoryConfiguration ldapConfiguration,
                                               HierarchicalConfiguration<ImmutableNode> listenerConfig) {
        return from(ldapConfiguration,
            listenerConfig.getString("firstnameAttribute", "givenName"),
            listenerConfig.getString("surnameAttribute", "sn"),
            Optional.ofNullable(listenerConfig.getStringArray("extraAttributes"))
                .<Collection<String>>map(LdapSignatureAttributes::splitAttributeNames)
                .orElse(ImmutableSet.of()));
    }

    // extraAttributes is documented as a comma-separated list: do not depend on the list delimiter of the configuration
    private static Collection<String> splitAttributeNames(String[] values) {
        return Arrays.stream(values)
            .flatMap(value -> Splitter.on(',').trimResults().omitEmptyStrings().splitToStream(value))
            .collect(ImmutableSet.toImmutableSet());
    }

    /**
     * Several IdentityProvisionListener can be registered: the attributes any of them reads can be interpolated.
     */
    public LdapSignatureAttributes union(LdapSignatureAttributes other) {
        return new LdapSignatureAttributes(firstnameAttribute, surnameAttribute, usernameAttribute,
            Sets.union(requestedAttributes, other.requestedAttributes).immutableCopy());
    }

    public static LdapSignatureAttributes from(LdapRepositoryConfiguration ldapConfiguration, Collection<String> extraAttributes) {
        return from(ldapConfiguration, "givenName", "sn", extraAttributes);
    }

    private static LdapSignatureAttributes from(LdapRepositoryConfiguration ldapConfiguration, String firstnameAttribute,
                                                String surnameAttribute, Collection<String> extraAttributes) {
        String usernameAttribute = ldapConfiguration.getUsernameAttribute().orElse(ldapConfiguration.getUserIdAttribute());
        Set<String> requestedAttributes = ImmutableSet.<String>builder()
            .addAll(extraAttributes)
            .add(firstnameAttribute)
            .add(surnameAttribute)
            .add(usernameAttribute)
            .build()
            .stream()
            .filter(attribute -> !isForbidden(attribute))
            .collect(ImmutableSet.toImmutableSet());
        return new LdapSignatureAttributes(firstnameAttribute, surnameAttribute, usernameAttribute, requestedAttributes);
    }

    private static boolean isForbidden(String attribute) {
        return FORBIDDEN_ATTRIBUTES.contains(attribute.toLowerCase(Locale.US));
    }

    public String[] asSearchAttributes() {
        return requestedAttributes.toArray(new String[0]);
    }

    /**
     * Values usable for {@code {ldap:<name>}} placeholders. Only the requested attributes are kept: an LDAP server
     * may return more than requested.
     */
    public Map<String, String> interpolationValues(SearchResultEntry entry) {
        return requestedAttributes.stream()
            .flatMap(requested -> valueOf(entry, requested).stream()
                .map(value -> Map.entry(requested, value)))
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // Placeholders use the configured name: match the returned attribute whatever its case, preferring the
    // value without option (e.g. "title" over "title;lang-fr")
    private static Optional<String> valueOf(SearchResultEntry entry, String requested) {
        return entry.getAttributes().stream()
            .filter(attribute -> attribute.getValue() != null)
            .filter(attribute -> attribute.getBaseName().equalsIgnoreCase(requested))
            .min(Comparator.comparing(Attribute::hasOptions))
            .map(Attribute::getValue);
    }
}
