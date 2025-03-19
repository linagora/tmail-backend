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

package com.linagora.calendar.restapi.routes;

import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JwtSigner {
    private final Clock clock;
    private final Duration tokenValidity;
    private final Key key;

    public JwtSigner(Clock clock, Duration tokenValidity, Key key) {
        this.clock = clock;
        this.tokenValidity = tokenValidity;
        this.key = key;
    }

    public String generate(String sub) {
        return Jwts.builder()
            .setHeaderParam("typ", "JWT")
            .claim("sub", sub)
            .signWith(key, SignatureAlgorithm.RS256)
            .setIssuedAt(Date.from(clock.instant()))
            .setExpiration(Date.from(clock.instant().plus(tokenValidity)))
            .compact();
    }
}
