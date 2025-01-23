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

package com.linagora.tmail.webadmin.contact.aucomplete;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;

import spark.Request;

public class ContactIndexingTaskRegistration extends TaskFromRequestRegistry.TaskRegistration {
    private static final String USERS_PER_SECOND_PARAMETER = "usersPerSecond";
    private static final TaskRegistrationKey CONTACT_INDEXING = TaskRegistrationKey.of("ContactIndexing");

    @Inject
    public ContactIndexingTaskRegistration(ContactIndexingService contactIndexingService) {
        super(CONTACT_INDEXING, request -> new ContactIndexingTask(contactIndexingService, parseRunningOptions(request)));
    }

    private static ContactIndexingTask.RunningOptions parseRunningOptions(Request request) {

        return intQueryParameter(request)
            .map(ContactIndexingTask.RunningOptions::of)
            .orElse(ContactIndexingTask.RunningOptions.DEFAULT);
    }

    private static Optional<Integer> intQueryParameter(Request request) {
        try {
            return Optional.ofNullable(request.queryParams(USERS_PER_SECOND_PARAMETER))
                .map(Integer::parseInt);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Illegal value supplied for query parameter '%s', expecting a " +
                "strictly positive optional integer", USERS_PER_SECOND_PARAMETER), e);
        }
    }
}