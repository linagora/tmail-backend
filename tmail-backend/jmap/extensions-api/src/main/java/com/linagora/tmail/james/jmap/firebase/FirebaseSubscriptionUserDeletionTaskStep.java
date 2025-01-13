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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.james.jmap.firebase;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.reactivestreams.Publisher;

public class FirebaseSubscriptionUserDeletionTaskStep implements DeleteUserDataTaskStep {
    private final FirebaseSubscriptionRepository firebaseSubscriptionRepository;

    @Inject
    public FirebaseSubscriptionUserDeletionTaskStep(FirebaseSubscriptionRepository firebaseSubscriptionRepository) {
        this.firebaseSubscriptionRepository = firebaseSubscriptionRepository;
    }

    @Override
    public StepName name() {
        return new StepName("FirebaseSubscriptionUserDeletionTaskStep");
    }

    @Override
    public int priority() {
        return 8;
    }

    @Override
    public Publisher<Void> deleteUserData(Username username) {
        return firebaseSubscriptionRepository.revoke(username);
    }
}
