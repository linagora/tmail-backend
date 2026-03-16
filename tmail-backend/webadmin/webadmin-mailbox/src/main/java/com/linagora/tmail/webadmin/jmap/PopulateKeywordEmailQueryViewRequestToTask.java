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

package com.linagora.tmail.webadmin.jmap;

import jakarta.inject.Inject;

import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;

public class PopulateKeywordEmailQueryViewRequestToTask extends TaskFromRequestRegistry.TaskRegistration {
    public static final TaskRegistrationKey POPULATE_KEYWORD_EMAIL_QUERY_VIEW = TaskRegistrationKey.of("populateKeywordEmailQueryView");

    @Inject
    public PopulateKeywordEmailQueryViewRequestToTask(KeywordEmailQueryViewPopulator populator) {
        super(POPULATE_KEYWORD_EMAIL_QUERY_VIEW,
            request -> new PopulateKeywordEmailQueryViewTask(populator, RunningOptionsParser.parse(request)));
    }
}
