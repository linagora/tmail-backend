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

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.push;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bson.Document;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import reactor.core.publisher.Mono;

public class DockerOpenPaasPopulateService {

    private static final Logger LOGGER = Logger.getLogger(DockerOpenPaasPopulateService.class.getName());

    private final MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
    private final MongoDatabase database = mongoClient.getDatabase("esn_docker");
    private final MongoCollection<Document> usersCollection = database.getCollection("users");
    private final MongoCollection<Document> domainsCollection = database.getCollection("domains");

    private Document getOpenPaasDomain() {
        return Mono.from(domainsCollection.find()
            .filter(new Document("name", "open-paas.org"))
            .first()).block();
    }

    private Mono<Document> joinDomain(Document user, Document domain) {
        return Mono.from(usersCollection
                .findOneAndUpdate(
                    eq("_id", user.get("_id")),
                    push("domains", new Document("domain_id", domain.get("_id")))));
    }

    public Mono<Document> createUser() {
        UUID randomUUID = UUID.randomUUID();
        Document userToSave = new Document()
                .append("firstname", "User_" + randomUUID)
                .append("lastname", "User_" + randomUUID)
                .append("password", "secret")
                .append("accounts", List.of(new Document()
                        .append("type", "email")
                        .append("emails", List.of("user_" + randomUUID + "@open-paas.org"))));

        return Mono.from(usersCollection.insertOne(userToSave))
            .then(joinDomain(userToSave, getOpenPaasDomain()));
    }
}