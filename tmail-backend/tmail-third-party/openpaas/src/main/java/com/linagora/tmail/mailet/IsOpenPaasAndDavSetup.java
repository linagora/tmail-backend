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

import java.util.Collection;
import java.util.Collections;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

public class IsOpenPaasAndDavSetup extends GenericMatcher {

    private final CalDavCollect.CalendarClients calendarClients;

    @Inject
    public IsOpenPaasAndDavSetup(CalDavCollect.CalendarClients calendarClients) {
        this.calendarClients = calendarClients;
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (calendarClients.getDavClient().isPresent()) {
            return mail.getRecipients();
        }
        return Collections.emptyList();
    }

}
