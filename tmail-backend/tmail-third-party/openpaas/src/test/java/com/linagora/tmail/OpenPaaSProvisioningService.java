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

package com.linagora.tmail;

import java.util.List;
import java.util.UUID;

import org.bson.Document;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;

import reactor.core.publisher.Mono;

public class OpenPaaSProvisioningService {
    private final MongoDatabase database;

    public OpenPaaSProvisioningService(String mongoUri) {
        MongoClient mongoClient = MongoClients.create(mongoUri);
        database = mongoClient.getDatabase("esn_docker");
    }

    private Document openPaasDomain() {
        return Mono.from(database.getCollection("domains").find()
            .filter(new Document("name", "open-paas.org"))
            .first()).block();
    }

    public Mono<OpenPaasUser> createUser() {
        UUID randomUUID = UUID.randomUUID();
        Document userToSave = new Document()
                .append("firstname", "User_" + randomUUID)
                .append("lastname", "User_" + randomUUID)
                .append("password", "secret")
                .append("domains",  List.of(new Document("domain_id", openPaasDomain().get("_id"))))
                .append("accounts", List.of(new Document()
                        .append("type", "email")
                        .append("emails", List.of("user_" + randomUUID + "@open-paas.org"))));

        return Mono.from(database.getCollection("users").insertOne(userToSave))
            .flatMap(success ->
                Mono.from(
                    database.getCollection("users").find(new Document("_id", success.getInsertedId())).first()))
            .map(OpenPaasUser::fromDocument);
    }
}