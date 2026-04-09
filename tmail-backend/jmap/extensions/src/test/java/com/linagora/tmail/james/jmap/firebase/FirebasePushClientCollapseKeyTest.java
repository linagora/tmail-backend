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

import static com.linagora.tmail.james.jmap.firebase.FirebasePushClient.computeCollapseKey;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.model.FirebaseToken;

class FirebasePushClientCollapseKeyTest {
    private static final FirebaseToken ANY_TOKEN = new FirebaseToken("any_token");

    @Test
    void collapseKeyShouldBeSha256Base64OfSortedKeysJoinedByNullByte() throws Exception {
        FirebasePushRequest request = new FirebasePushRequest(
            Map.of("b:Email", "s1", "a:Email", "s2"),
            ANY_TOKEN, FirebasePushUrgency.NORMAL);

        String collapseKey = computeCollapseKey(request);

        String joined = "a:Email\0b:Email";
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
        String expected = Base64.getEncoder().encodeToString(hash);

        assertThat(collapseKey).isEqualTo(expected);
    }

    @Test
    void collapseKeyShouldBeDeterministic() {
        FirebasePushRequest request = new FirebasePushRequest(
            Map.of("b:Email", "s1", "a:Email", "s2"),
            ANY_TOKEN, FirebasePushUrgency.NORMAL);

        assertThat(computeCollapseKey(request))
            .isEqualTo(computeCollapseKey(request));
    }

    @Test
    void collapseKeyShouldDifferWhenKeysDiffer() {
        FirebasePushRequest request1 = new FirebasePushRequest(
            Map.of("a:Email", "s1"), ANY_TOKEN, FirebasePushUrgency.NORMAL);
        FirebasePushRequest request2 = new FirebasePushRequest(
            Map.of("b:Email", "s1"), ANY_TOKEN, FirebasePushUrgency.NORMAL);

        assertThat(computeCollapseKey(request1))
            .isNotEqualTo(computeCollapseKey(request2));
    }

    @Test
    void collapseKeyShouldBeIndependentOfKeyOrder() {
        FirebasePushRequest request1 = new FirebasePushRequest(
            Map.of("a:Email", "s1", "b:Email", "s2"),
            ANY_TOKEN, FirebasePushUrgency.NORMAL);
        FirebasePushRequest request2 = new FirebasePushRequest(
            Map.of("b:Email", "s2", "a:Email", "s1"),
            ANY_TOKEN, FirebasePushUrgency.NORMAL);

        assertThat(computeCollapseKey(request1))
            .isEqualTo(computeCollapseKey(request2));
    }

    @Test
    void collapseKeyShouldBeSameRegardlessOfValues() {
        FirebasePushRequest request1 = new FirebasePushRequest(
            Map.of("a:Email", "stateA"), ANY_TOKEN, FirebasePushUrgency.NORMAL);
        FirebasePushRequest request2 = new FirebasePushRequest(
            Map.of("a:Email", "stateB"), ANY_TOKEN, FirebasePushUrgency.NORMAL);

        assertThat(computeCollapseKey(request1))
            .isEqualTo(computeCollapseKey(request2));
    }
}
