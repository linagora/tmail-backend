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

package com.linagora.tmail.cassandra;

import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMESTAMP;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMEUUID;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.init.CassandraTableManager;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.util.DurationParser;
import org.apache.james.utils.UserDefinedStartable;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.api.MailReportEntry;
import com.linagora.tmail.api.MailReportGenerator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailReportGenerator implements MailReportGenerator, UserDefinedStartable {
    public static final CqlIdentifier ID = CqlIdentifier.fromCql("id");
    public static final String TABLE_NAME = "mu_mail_report";
    public static final CqlIdentifier KIND = CqlIdentifier.fromCql("kind");
    public static final CqlIdentifier SENDER = CqlIdentifier.fromCql("sender");
    public static final CqlIdentifier RECIPIENT = CqlIdentifier.fromCql("recipient");
    public static final CqlIdentifier SUBJECT = CqlIdentifier.fromCql("subject");
    public static final CqlIdentifier DATE = CqlIdentifier.fromCql("date");
    public static final CqlIdentifier SIZE = CqlIdentifier.fromCql("size");
    public static final int TTL = (int) DurationParser.parse(System.getProperty("mu.report.retention", "365d")).getSeconds();
    public static final CassandraDataDefinition MODULE = CassandraDataDefinition.builder()
        .table(TABLE_NAME)
        .comment("MU sent and received mail audit trail")
        .options(options -> options.withGcGraceSeconds(0))
        .statement(statement -> types -> statement
            .withPartitionKey(ID, TIMEUUID)
            .withColumn(KIND, TEXT)
            .withColumn(SENDER, TEXT)
            .withColumn(RECIPIENT, TEXT)
            .withColumn(SUBJECT, TEXT)
            .withColumn(SIZE, DataTypes.BIGINT)
            .withColumn(DATE, TIMESTAMP))
        .build();

    private final CqlSession session;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraTypesProvider typesProvider;
    private final DriverExecutionProfile batchProfile;
    private PreparedStatement insert;
    private PreparedStatement selectByDateBetween;

    @Inject
    public CassandraMailReportGenerator(CqlSession session,
                                        CassandraTypesProvider typesProvider) {
        this.session = session;
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.typesProvider = typesProvider;
        this.batchProfile = JamesExecutionProfiles.getBatchProfile(session);
    }

    @Override
    public void start() {
        new CassandraTableManager(MODULE, session).initializeTables(typesProvider);

        this.insert = prepareInsert();
        this.selectByDateBetween = prepareSelectByDateBetween();
    }

    private PreparedStatement prepareInsert() {
        return session.prepare(insertInto(TABLE_NAME)
            .value(ID, bindMarker(ID))
            .value(KIND, bindMarker(KIND))
            .value(SENDER, bindMarker(SENDER))
            .value(RECIPIENT, bindMarker(RECIPIENT))
            .value(SUBJECT, bindMarker(SUBJECT))
            .value(DATE, bindMarker(DATE))
            .value(SIZE, bindMarker(SIZE))
            .usingTtl(TTL)
            .build());
    }

    private PreparedStatement prepareSelectByDateBetween() {
        return session.prepare(selectFrom(TABLE_NAME)
            .all()
            .build());
    }

    @Override
    public Mono<Void> append(MailReportEntry entry) {
        return cassandraAsyncExecutor.executeVoid(insert.bind()
            .setUuid(ID, Uuids.timeBased())
            .setString(KIND, entry.kind().asString())
            .setString(SENDER, entry.sender().asString())
            .setString(RECIPIENT, entry.recipient().asString())
            .setString(SUBJECT, entry.subject())
            .setLong(SIZE, entry.size())
            .setInstant(DATE, entry.date()));
    }

    @Override
    public Flux<MailReportEntry> generateReport(Instant start, Instant end) {
        return cassandraAsyncExecutor.executeRows(selectByDateBetween.bind().setExecutionProfile(batchProfile))
            .filter(isBetweenDate(start, end))
            .handle((row, sink) -> MailReportEntry.Kind.parse(row.getString(KIND))
                .flatMap(Throwing.function(kind -> Optional.of(new MailReportEntry(kind,
                    row.getString(SUBJECT),
                    MaybeSender.getMailSender(row.getString(SENDER)),
                    Optional.ofNullable(row.getString(RECIPIENT)).map(Throwing.function(MailAddress::new)).orElse(null),
                    row.getInstant(DATE),
                    row.getLong(SIZE)))))
                .ifPresent(sink::next));
    }

    private Predicate<Row> isBetweenDate(Instant start, Instant end) {
        return row -> {
            Instant date = row.getInstant(DATE);
            return !date.isBefore(start) && !date.isAfter(end);
        };
    }
}