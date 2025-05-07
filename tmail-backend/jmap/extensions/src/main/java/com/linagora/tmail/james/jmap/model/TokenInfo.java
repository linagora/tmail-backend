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

package com.linagora.tmail.james.jmap.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.google.common.base.MoreObjects;

public record TokenInfo(String email, Optional<Sid> sid, Instant exp, List<Aud> aud) {

    public String asString() {
        return MoreObjects.toStringHelper(this)
            .add("email", email)
            .add("sid", Optional.ofNullable(sid).flatMap(sidValue -> sidValue.map(Sid::value)).orElse(null))
            .add("exp", exp)
            .add("aud", Optional.ofNullable(aud).orElseGet(List::of).stream().map(Aud::value).toList())
            .toString();
    }
}
