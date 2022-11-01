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

    Publisher<FirebaseSubscription> get(Username username, Set<FirebaseSubscriptionId> ids);

    Publisher<FirebaseSubscription> list(Username username);
}
