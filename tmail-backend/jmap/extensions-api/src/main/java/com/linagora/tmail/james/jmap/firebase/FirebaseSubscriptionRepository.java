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

package com.linagora.tmail.james.jmap.firebase;

import java.time.ZonedDateTime;
import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.TypeName;
import org.reactivestreams.Publisher;

import com.linagora.tmail.james.jmap.model.FirebaseSubscription;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionCreationRequest;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionExpiredTime;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionId;

public interface FirebaseSubscriptionRepository {
    Publisher<FirebaseSubscription> save(Username username, FirebaseSubscriptionCreationRequest firebaseSubscriptionCreationRequest);

    Publisher<FirebaseSubscriptionExpiredTime> updateExpireTime(Username username, FirebaseSubscriptionId id, ZonedDateTime newExpire);

    Publisher<Void> updateTypes(Username username, FirebaseSubscriptionId id, Set<TypeName> types);

    Publisher<Void> revoke(Username username, FirebaseSubscriptionId id);

    Publisher<Void> revoke(Username username);

    Publisher<FirebaseSubscription> get(Username username, Set<FirebaseSubscriptionId> ids);

    Publisher<FirebaseSubscription> list(Username username);
}
