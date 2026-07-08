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

package com.linagora.tmail.webadmin.mailinglist;

/**
 * Thrown when the mailing list routes are queried while no {@code mailingLists.properties} file (or no {@code baseDN})
 * is configured. Translated to a {@code 409 Conflict} by the routes.
 */
public class MailingListsNotConfiguredException extends RuntimeException {
    public MailingListsNotConfiguredException() {
        super("Mailing lists are not configured. Please provide a mailingLists.properties file with a baseDN entry.");
    }
}
