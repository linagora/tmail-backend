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

package com.linagora.tmail.james.jmap.calendar

import java.time.DateTimeException

import net.fortuna.ical4j.validate.ValidationResult

import scala.jdk.CollectionConverters._

object CalendarValidation {
  private val ERROR_PREFIX: String = "Invalidate calendar event: "

  /**
   * ical4j 4 lazily parses date and time zone values: validating them can throw a
   * `DateTimeException` instead of reporting an error in the `ValidationResult`.
   *
   * @throws IllegalArgumentException when the validation reports errors or fails to parse a value
   */
  def requireValid(validation: => Seq[ValidationResult]): Unit = {
    val errors: Seq[ValidationResult] = validationErrors(validation)
    if (errors.nonEmpty) {
      throw new IllegalArgumentException(ERROR_PREFIX + asString(errors))
    }
  }

  private def validationErrors(validation: => Seq[ValidationResult]): Seq[ValidationResult] =
    try {
      validation.filter(_.hasErrors)
    } catch {
      case e: DateTimeException => throw new IllegalArgumentException(ERROR_PREFIX + e.getMessage, e)
    }

  private def asString(errors: Seq[ValidationResult]): String =
    errors.flatMap(_.getEntries.asScala.toSeq)
      .map(error => s"${error.getContext} : ${error.getMessage}")
      .mkString(";")
}
