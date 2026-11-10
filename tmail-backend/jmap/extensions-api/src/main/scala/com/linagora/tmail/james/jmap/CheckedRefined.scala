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

package com.linagora.tmail.james.jmap

import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.refineV

/**
 * Scala 3 replacement for the literal refinement of `eu.timepit.refined.auto`, a Scala 2 macro checking
 * the literal at compile time. The check runs here when the value is built: a value that does not satisfy
 * the predicate throws rather than going through.
 */
object CheckedRefined {
  given checkedRefinement[T, P](using Validate[T, P]): Conversion[T, T Refined P] = value =>
    refineV[P](value).fold(error => throw new IllegalArgumentException(error), identity)
}
