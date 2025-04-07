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

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.bson.Document;
import org.bson.types.ObjectId;

import com.linagora.calendar.storage.OpenPaaSId;
import com.linagora.calendar.storage.OpenPaaSUser;
import com.linagora.calendar.storage.OpenPaaSUserDAO;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.MongoDatabase;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MongoDBOpenPaaSUserDAO implements OpenPaaSUserDAO {
    public static final String COLLECTION = "users";

    private final MongoDatabase database;
    private final MongoDBOpenPaaSDomainDAO domainDAO;

    @Inject
    public MongoDBOpenPaaSUserDAO(MongoDatabase database, MongoDBOpenPaaSDomainDAO domainDAO) {
        this.database = database;
        this.domainDAO = domainDAO;
    }

    @Override
    public Mono<OpenPaaSUser> retrieve(OpenPaaSId id) {
        return Mono.from(database.getCollection(COLLECTION)
                .find(Filters.eq("_id", new ObjectId(id.value())))
                .first())
            .map(document -> new OpenPaaSUser(
                Username.of(document.getList("accounts", Document.class).get(0).getList("emails", String.class).get(0)),
                new OpenPaaSId(document.getObjectId("_id").toHexString()),
                document.getString("firstname"), document.getString("lastname")));
    }

    @Override
    public Mono<OpenPaaSUser> retrieve(Username username) {
        return Mono.from(database.getCollection(COLLECTION)
                .find(Filters.eq("accounts.emails", username.asString()))
                .first())
            .map(document -> new OpenPaaSUser(username, new OpenPaaSId(document.getObjectId("_id").toHexString()),
                document.getString("firstname"), document.getString("lastname")));
    }

    @Override
    public Mono<OpenPaaSUser> add(Username username) {
        return domainDAO.retrieve(username.getDomainPart().get())
            .switchIfEmpty(Mono.error(() -> new IllegalStateException(username.getDomainPart().get().asString() + " does not exist")))
            .map(domain -> new Document()
                .append("firstname", username.asString())
                .append("lastname", username.asString())
                .append("password", "secret")
                .append("email", username.asString()) // not part of OpenPaaS datamodel but helps solve concurrency
                .append("domains",  List.of(new Document("domain_id", new ObjectId(domain.id().value()))))
                .append("accounts", List.of(new Document()
                    .append("type", "email")
                    .append("emails", List.of(username.asString())))))
            .flatMap(document -> Mono.from(database.getCollection(COLLECTION).insertOne(document)))
            .map(InsertOneResult::getInsertedId)
            .map(id -> new OpenPaaSUser(username, new OpenPaaSId(id.asObjectId().getValue().toHexString()), username.asString(), username.asString()))
            .onErrorResume(e -> {
                if (e.getMessage().contains("E11000 duplicate key error collection")) {
                    return Mono.error(new IllegalStateException(username.asString() + " already exists"));
                }
                return Mono.error(e);
            });
    }

    @Override
    public Flux<OpenPaaSUser> list() {
        return Flux.from(database.getCollection(COLLECTION).find())
            .map(document -> new OpenPaaSUser(
                Username.of(document.getList("accounts", Document.class).get(0).getList("emails", String.class).get(0)),
                new OpenPaaSId(document.getObjectId("_id").toHexString()),
                document.getString("firstname"), document.getString("lastname")));
    }
}
