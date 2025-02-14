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

package com.linagora.tmail.james.jmap.model

import java.util.Locale

import scala.util.Try

object LanguageLocation {
  def fromString(languageCode: String): Try[LanguageLocation] = detectLocale(languageCode).map(LanguageLocation.apply)

  def detectLocale(languageCode: String): Try[Locale] =
    if (Locale.getISOLanguages.contains(languageCode)) {
      Try(Locale.forLanguageTag(languageCode))
    } else {
      throw new IllegalArgumentException("The language must be a valid ISO language code")
    }
}

case class LanguageLocation(language: Locale) {
  def value: String = language.toLanguageTag
}