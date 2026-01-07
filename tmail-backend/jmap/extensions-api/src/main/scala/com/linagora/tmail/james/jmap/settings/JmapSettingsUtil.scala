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

package com.linagora.tmail.james.jmap.settings

import java.util.{Locale, Optional}

import com.linagora.tmail.james.jmap.settings.TWPReadOnlyPropertyProvider.TWP_SETTINGS_VERSION
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.javaapi.OptionConverters

object JmapSettingsUtil {
  def getTWPSettingsVersion(settings: JmapSettings): Option[java.lang.Long] =
    settings.settings.get(TWP_SETTINGS_VERSION) match {
      case Some(value) => Some(value.value.toLong)
      case None => None
    }

  def parseLocaleFromSettings(settings: JmapSettings, defaultLanguage: Locale): Optional[Locale] =
    OptionConverters.toJava(settings.language()
      .map(language => LocaleUtil.toLocaleRelaxedly(language, defaultLanguage)))
}

object LocaleUtil {
  private val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  def toLocaleStrictly(language: String): Locale = {
    if (Locale.getISOLanguages.exists(localeLanguage => localeLanguage.equalsIgnoreCase(language))) {
      Locale.forLanguageTag(language)
    } else {
      throw new IllegalArgumentException(s"The provided language '$language' can not be parsed to a valid Locale.")
    }
  }

  def toLocaleRelaxedly(language: String, defaultLanguage: Locale): Locale = {
    if (Locale.getISOLanguages.exists(localeLanguage => localeLanguage.equalsIgnoreCase(language))) {
      return Locale.forLanguageTag(language)
    }
    LOGGER.info("The provided language '{}' can not be parsed to a valid Locale. Falling back to default locale '{}'", language, defaultLanguage)
    defaultLanguage
  }
}
