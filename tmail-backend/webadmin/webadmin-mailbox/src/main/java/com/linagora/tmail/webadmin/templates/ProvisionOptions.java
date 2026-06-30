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

package com.linagora.tmail.webadmin.templates;

/**
 * Options driving how reference templates are applied to a target mailbox.
 *
 * @param overwriteExisting when {@code true}, a target template sharing its {@code Message-Id} with a reference
 *                          template is replaced by the reference one. When {@code false} it is left untouched (skipped).
 * @param prune             when {@code true}, target templates whose {@code Message-Id} is not part of the reference
 *                          folder anymore are deleted (full mirror). When {@code false} the operation is copy-only.
 */
public record ProvisionOptions(boolean overwriteExisting, boolean prune) {
    public static final ProvisionOptions DEFAULT = new ProvisionOptions(false, false);
}
