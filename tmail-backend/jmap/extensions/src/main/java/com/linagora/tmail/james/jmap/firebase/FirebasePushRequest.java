package com.linagora.tmail.james.jmap.firebase;

import java.util.Map;

import com.linagora.tmail.james.jmap.model.FirebaseToken;

public record FirebasePushRequest(Map<String, String> stateChangesMap, FirebaseToken token) {
}
