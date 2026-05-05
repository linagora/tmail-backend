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

package com.linagora.tmail.james.jmap.event

import java.util.{Locale, Optional}

import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText

import scala.jdk.CollectionConverters._

class DomainSignatureTemplate(val templates: java.util.Map[Locale, SignatureText]) {

  def forLocale(locale: Locale, defaultLocale: Locale): Optional[SignatureText] =
    Optional.ofNullable(templates.get(locale))
      .or(() => Optional.ofNullable(templates.get(defaultLocale)))

  override def equals(obj: Any): Boolean = obj match {
    case other: DomainSignatureTemplate => templates == other.templates
    case _ => false
  }

  override def hashCode(): Int = templates.hashCode()

  override def toString: String = s"DomainSignatureTemplate($templates)"
}

object DomainSignatureTemplate {
  def apply(templates: Map[Locale, SignatureText]): DomainSignatureTemplate =
    new DomainSignatureTemplate(templates.asJava)
}
