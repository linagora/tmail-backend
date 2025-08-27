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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class LocaleParsingTest {
    @ParameterizedTest
    @ValueSource(strings = {"fr", "FR"})
    void parseFrenchLanguageSettingTest(String language) {
        assertThat(I18NDSNBounce.toLocaleStrictly(language))
            .isEqualTo(Locale.FRENCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "EN"})
    void parseEnglishLanguageSettingTest(String language) {
        assertThat(I18NDSNBounce.toLocaleStrictly(language))
            .isEqualTo(Locale.ENGLISH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "RU"})
    void parseRussiaLanguageSettingTest(String language) {
        assertThat(I18NDSNBounce.toLocaleStrictly(language))
            .isEqualTo(Locale.of("ru"));
    }

    @Test
    void parseInvalidLanguageSettingShouldBeResilientAndFallbackToDefaultLanguageWhenRelaxMode() {
        assertThat(I18NDSNBounce.toLocaleRelaxedly("invalid", Locale.ENGLISH))
            .isEqualTo(Locale.ENGLISH);
    }

    @Test
    void parseInvalidLanguageSettingShouldThrowWhenStrictMode() {
        assertThatThrownBy(() -> I18NDSNBounce.toLocaleStrictly("invalid"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseNullLanguageShouldBeResilientAndFallbackToDefaultLanguageWhenRelaxMode() {
        assertThat(I18NDSNBounce.toLocaleRelaxedly(null, Locale.ENGLISH))
            .isEqualTo(Locale.ENGLISH);
    }
}
