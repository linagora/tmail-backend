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

package com.linagora.tmail.james.jmap.domainsignature;

public record ApplyResult(int applied, int skipped, int error) {
    public static final ApplyResult APPLIED = new ApplyResult(1, 0, 0);
    public static final ApplyResult SKIPPED = new ApplyResult(0, 1, 0);
    public static final ApplyResult ERROR   = new ApplyResult(0, 0, 1);

    public ApplyResult merge(ApplyResult other) {
        return new ApplyResult(
            applied + other.applied,
            skipped + other.skipped,
            error   + other.error);
    }
}
