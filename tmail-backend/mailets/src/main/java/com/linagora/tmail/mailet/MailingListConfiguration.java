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

import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

/**
 * <p>Centralized configuration for mailing list related components (the {@code LDAPMailingList} and
 * {@code OBMLDAPMailingList} mailets, the {@code TMailWithMailingListValidRcptHandler} SMTP handler and the
 * {@code /mailingLists} webadmin routes).</p>
 *
 * <p>This configuration is loaded from a {@code mailingLists.properties} file:</p>
 *
 * <pre><code>
 * # Base DN of the subtree holding the mailing list groups. Optional: when set, mailing list components no
 * # longer require a per-component baseDN and fall back to this value.
 * baseDN=ou=lists,dc=linagora,dc=com
 *
 * # LDAP attribute holding the mail address of a group. Optional, defaults to "mail".
 * mailAttributeForGroups=mail
 *
 * # Whether mailing lists follow the OBM schema (obmGroup / mailBox / externalContactEmail) rather than the
 * # default groupOfNames schema. Optional, defaults to false.
 * obm.compatibility=false
 * </code></pre>
 */
public record MailingListConfiguration(Optional<String> baseDN,
                                       String mailAttributeForGroups,
                                       boolean obmCompatibility) {
    public static final String DEFAULT_MAIL_ATTRIBUTE_FOR_GROUPS = "mail";

    /**
     * An empty configuration: no baseDN override, default mail attribute, no OBM compatibility. Used as a fallback
     * when no {@code mailingLists.properties} file is provided.
     */
    public static final MailingListConfiguration EMPTY =
        new MailingListConfiguration(Optional.empty(), DEFAULT_MAIL_ATTRIBUTE_FOR_GROUPS, false);

    public static MailingListConfiguration from(Configuration configuration) {
        return new MailingListConfiguration(
            Optional.ofNullable(configuration.getString("baseDN", null))
                .map(String::trim)
                .filter(value -> !value.isEmpty()),
            configuration.getString("mailAttributeForGroups", DEFAULT_MAIL_ATTRIBUTE_FOR_GROUPS),
            configuration.getBoolean("obm.compatibility", false));
    }

    /**
     * Resolves the effective baseDN: the per-component value when provided, otherwise the centralized value from
     * {@code mailingLists.properties}.
     */
    public Optional<String> resolveBaseDN(Optional<String> componentBaseDN) {
        return componentBaseDN.or(this::baseDN);
    }

    /**
     * Resolves the effective mail attribute for groups: the per-component value when provided, otherwise the
     * centralized value from {@code mailingLists.properties}.
     */
    public String resolveMailAttributeForGroups(Optional<String> componentValue) {
        return componentValue.orElseGet(this::mailAttributeForGroups);
    }
}
