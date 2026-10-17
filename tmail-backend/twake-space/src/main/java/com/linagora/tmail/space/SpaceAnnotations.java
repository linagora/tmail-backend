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

package com.linagora.tmail.space;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.mailbox.ReadOnlyAnnotationPredicate;
import org.apache.james.mailbox.model.MailboxAnnotationKey;

import com.google.common.collect.ImmutableSet;

/**
 * The space binding of a team mailbox, stored as annotations of its root mailbox. Clients can read them, only TMail
 * writes them.
 */
public final class SpaceAnnotations {
    public static final String PREFIX = "/shared/vendor/twake/";
    public static final MailboxAnnotationKey ORGANIZATION_ID = new MailboxAnnotationKey(PREFIX + "organizationId");
    public static final MailboxAnnotationKey SPACE_ID = new MailboxAnnotationKey(PREFIX + "spaceId");
    public static final MailboxAnnotationKey DELETED_AT = new MailboxAnnotationKey(PREFIX + "deletedAt");
    public static final ImmutableSet<MailboxAnnotationKey> KEYS = ImmutableSet.of(ORGANIZATION_ID, SPACE_ID, DELETED_AT);

    public static final ReadOnlyAnnotationPredicate READ_ONLY = key -> key.asString().startsWith(PREFIX);

    /** What a team mailbox root says about its space. */
    public record SpaceBinding(String organizationId, String spaceId, Optional<Instant> deletedAt) {
        public boolean isLive() {
            return deletedAt.isEmpty();
        }
    }

    private SpaceAnnotations() {
    }
}
