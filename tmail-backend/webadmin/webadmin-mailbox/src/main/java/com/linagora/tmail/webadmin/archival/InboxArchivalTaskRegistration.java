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

package com.linagora.tmail.webadmin.archival;

import jakarta.inject.Inject;

import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;

public class InboxArchivalTaskRegistration extends TaskFromRequestRegistry.TaskRegistration {
    private static final TaskRegistrationKey INBOX_ARCHIVAL = TaskRegistrationKey.of("InboxArchival");

    @Inject
    public InboxArchivalTaskRegistration(InboxArchivalService inboxArchivalService) {
        super(INBOX_ARCHIVAL, request -> new InboxArchivalTask(inboxArchivalService));
    }
}
