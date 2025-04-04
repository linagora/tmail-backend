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

package com.linagora.calendar.storage.mongodb;

import org.bson.Document;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoDatabase;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MongoDBCollectionFactory {

    public static final String USERS = "users";
    public static final String DOMAINS = "domains";

    public static void initialize(MongoDatabase database) {
        createUsersCollection(database);
        createDomainsCollection(database);
    }

    private static void createUsersCollection(MongoDatabase database) {
        if (!collectionExists(database, USERS)) {
            Mono.from(database.createCollection(USERS))
                .block();
        }

        Mono.from(database.getCollection(USERS)
            .createIndex(new Document("email", 1), new IndexOptions().unique(true)))
            .block();
    }

    private static void createDomainsCollection(MongoDatabase database) {
        if (!collectionExists(database, DOMAINS)) {
            Mono.from(database.createCollection(DOMAINS))
                .block();
        }

        Mono.from(database.getCollection(DOMAINS)
            .createIndex(new Document("name", 1), new IndexOptions().unique(true)))
            .block();
    }

    private static boolean collectionExists(MongoDatabase database, String collectionName) {
        return Flux.from(database.listCollectionNames())
            .filter(collectionName::equals)
            .next()
            .blockOptional()
            .isPresent();
    }
}
