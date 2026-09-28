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
package com.linagora.tmail.rate.limiter.api.model;

import java.util.Optional;

/**
 * Update of a single optional limit, distinguishing a field left untouched from a field
 * explicitly set (to a value, or cleared).
 */
public sealed interface LimitUpdate {
    record Keep() implements LimitUpdate {
        @Override
        public Optional<Long> applyTo(Optional<Long> current) {
            return current;
        }
    }

    record Replace(Optional<Long> value) implements LimitUpdate {
        @Override
        public Optional<Long> applyTo(Optional<Long> current) {
            return value;
        }
    }

    LimitUpdate KEEP = new Keep();

    static LimitUpdate replace(long value) {
        return new Replace(Optional.of(value));
    }

    static LimitUpdate clear() {
        return new Replace(Optional.empty());
    }

    Optional<Long> applyTo(Optional<Long> current);
}
