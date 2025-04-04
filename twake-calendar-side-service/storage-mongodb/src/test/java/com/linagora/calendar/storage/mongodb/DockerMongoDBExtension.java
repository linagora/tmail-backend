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
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.MongoDBContainer;

import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;

import reactor.core.publisher.Mono;

public class DockerMongoDBExtension implements AfterEachCallback {
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.0.10");

    static {
        mongoDBContainer.start();
        init();
    }

    private static MongoDatabase db;

    static void init() {
        mongoDBContainer.start();
        db = MongoClients.create(mongoDBContainer.getConnectionString()).getDatabase("esn_docker");
        MongoDBCollectionFactory.initialize(db);
    }

    MongoDatabase openDatabaseConnection() {
        return db;
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        Mono.from(db.getCollection("domains").deleteMany(new Document())).block();
        Mono.from(db.getCollection("users").deleteMany(new Document())).block();
    }

    public MongoDatabase getDb() {
        return db;
    }
}
