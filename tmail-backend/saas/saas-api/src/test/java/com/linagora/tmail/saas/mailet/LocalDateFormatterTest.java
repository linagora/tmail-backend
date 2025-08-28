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

package com.linagora.tmail.saas.mailet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Locale;

import org.junit.jupiter.api.Test;

public class LocalDateFormatterTest {
    @Test
    void englishLocalDateFormatTest() {
        ZonedDateTime zonedDateTime = ZonedDateTime.parse("2015-10-30T14:12:00Z");

        assertThat(zonedDateTime.format(I18NDSNBounce.localizedDateFormatter(Locale.ENGLISH)))
            .isEqualTo("Friday, 30 October 2015 14:12:00 Z");
    }

    @Test
    void frenchLocalDateFormatTest() {
        ZonedDateTime zonedDateTime = ZonedDateTime.parse("2015-10-30T14:12:00Z");

        assertThat(zonedDateTime.format(I18NDSNBounce.localizedDateFormatter(Locale.FRENCH)))
            .isEqualTo("vendredi, 30 octobre 2015 14:12:00 Z");
    }
}
