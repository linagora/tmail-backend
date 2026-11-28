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

import java.util.List;
import java.util.Optional;

import org.apache.james.core.Domain;

import com.google.common.base.Preconditions;

/** A space as ldap-rest gives it in {@code twake.space.created} and {@code twake.space.synced}. */
public record SpaceSnapshot(String organizationId, Domain domain, String spaceId, String name, List<Member> members) {
    public enum Role {
        VIEWER("viewer"),
        EDITOR("editor"),
        ADMIN("admin");

        public static Optional<Role> parse(String value) {
            for (Role role : values()) {
                if (role.value.equals(value)) {
                    return Optional.of(role);
                }
            }
            return Optional.empty();
        }

        private final String value;

        Role(String value) {
            this.value = value;
        }
    }

    public record Member(String email, Role role) {
    }

    public SpaceRef ref() {
        return new SpaceRef(organizationId, spaceId);
    }

    public SpaceSnapshot {
        Preconditions.checkNotNull(organizationId);
        Preconditions.checkNotNull(domain);
        Preconditions.checkNotNull(spaceId);
        Preconditions.checkNotNull(name);
        members = List.copyOf(members);
    }
}
