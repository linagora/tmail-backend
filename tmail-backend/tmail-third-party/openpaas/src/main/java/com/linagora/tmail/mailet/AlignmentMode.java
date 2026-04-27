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

public enum AlignmentMode {
    STRICT, SAME_DOMAIN, NONE;

    public static final String PARAMETER_NAME = "alignmentMode";

    public static AlignmentMode fromString(String value) {
        return switch (value.toLowerCase()) {
            case "strict" -> STRICT;
            case "samedomain" -> SAME_DOMAIN;
            case "none" -> NONE;
            default -> throw new IllegalArgumentException(
                "Unknown alignmentMode '" + value + "'. Valid values: strict, sameDomain, none");
        };
    }
}
