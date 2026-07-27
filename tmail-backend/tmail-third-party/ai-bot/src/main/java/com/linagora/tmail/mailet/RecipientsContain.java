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

/**
 * Backward-compatible wrapper for the renamed RecipientsContain.
 *
 * @deprecated Use {@link com.linagora.tmail.aibotreplier.RecipientsContain} instead.
 * This class is kept only for backward compatibility with existing deployments
 * that reference the old fully qualified class name.
 */
@Deprecated
public class RecipientsContain extends com.linagora.tmail.aibotreplier.RecipientsContain {
}
