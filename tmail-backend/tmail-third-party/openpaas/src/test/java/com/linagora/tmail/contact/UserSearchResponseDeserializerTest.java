package com.linagora.tmail.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

public class UserSearchResponseDeserializerTest {

    private UserSearchResponse.Deserializer testee = new UserSearchResponse.Deserializer();

    @Test
    void deserializeShouldWork() throws Exception {
        String json = """
            [
                {
                    "_id": "676175e4aea7130059d339b6",
                    "firstname": "John1",
                    "lastname": "Doe1",
                    "preferredEmail": "user1@open-paas.org",
                    "emails": [
                        "user1@open-paas.org"
                    ],
                    "domains": [
                        {
                            "joined_at": "2024-12-17T13:00:22.766Z",
                            "domain_id": "676175e3aea7130059d339b2"
                        }
                    ],
                    "states": [],
                    "avatars": [],
                    "id": "676175e4aea7130059d339b6",
                    "displayName": "John1 Doe1",
                    "objectType": "user"
                }
            ]""";

        List<UserSearchResponse> deserialize = testee.deserialize(json.getBytes());
        assertThat(deserialize).hasSize(1);
        UserSearchResponse userSearchResponse = deserialize.get(0);
        assertThat(userSearchResponse.id()).isEqualTo("676175e4aea7130059d339b6");
        assertThat(userSearchResponse.preferredEmail()).isEqualTo("user1@open-paas.org");
    }

    @Test
    void deserializeShouldWorkWhenEmpty() throws Exception {
        String json = "[]";
        List<UserSearchResponse> deserialize = testee.deserialize(json.getBytes());
        assertThat(deserialize).isEmpty();
    }
}
