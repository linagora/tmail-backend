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

import java.util.Date;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.bson.Document;
import org.bson.types.ObjectId;

import com.google.common.collect.ImmutableList;
import com.linagora.calendar.storage.OpenPaaSDomain;
import com.linagora.calendar.storage.OpenPaaSDomainDAO;
import com.linagora.calendar.storage.OpenPaaSId;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.MongoDatabase;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MongoDBOpenPaaSDomainDAO implements OpenPaaSDomainDAO {
    public static final String COLLECTION = "domains";

    private final MongoDatabase database;

    @Inject
    public MongoDBOpenPaaSDomainDAO(MongoDatabase database) {
        this.database = database;
    }

    @Override
    public Mono<OpenPaaSDomain> retrieve(OpenPaaSId id) {
        return Mono.from(database.getCollection(COLLECTION)
                .find(Filters.eq("_id", new ObjectId(id.value())))
                .first())
            .map(document -> new OpenPaaSDomain(Domain.of(document.getString("name")), id));
    }

    @Override
    public Mono<OpenPaaSDomain> retrieve(Domain domain) {
        return Mono.from(database.getCollection(COLLECTION)
                .find(Filters.eq("name", domain.asString()))
            .first())
            .map(document -> new OpenPaaSDomain(domain, new OpenPaaSId(document.getObjectId("_id").toHexString())));
    }

    @Override
    public Mono<OpenPaaSDomain> add(Domain domain) {
        Document document = new Document()
            .append("timestamp", new Document()
                .append("creation", new Date()))
            .append("hostnames", ImmutableList.of())
            .append("name", domain.asString())
            .append("company_name", domain.asString())
            .append("administrators", ImmutableList.of());

        return Mono.from(database.getCollection(COLLECTION).insertOne(document))
            .map(InsertOneResult::getInsertedId)
            .map(id -> new OpenPaaSDomain(domain, new OpenPaaSId(id.asObjectId().getValue().toHexString())))
            .onErrorResume(e -> {
                if (e.getMessage().contains("E11000 duplicate key error collection")) {
                    return Mono.error(new IllegalStateException(domain.asString() + " already exists"));
                }
                return Mono.error(e);
            });
    }

    @Override
    public Flux<OpenPaaSDomain> list() {
        return Flux.from(database.getCollection(COLLECTION).find())
            .map(document -> new OpenPaaSDomain(
                Domain.of(document.getString("name")),
                new OpenPaaSId(document.getObjectId("_id").toHexString())));
    }
}
