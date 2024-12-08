package com.linagora.tmail.cassandra;

import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMESTAMP;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMEUUID;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.linagora.tmail.cassandra.MailReportGeneratorUtils.YEAR_MONTH_FORMATTER;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraTableManager;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.util.DurationParser;
import org.apache.james.utils.UserDefinedStartable;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
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
    public static final CqlIdentifier DATE_YEAR_MONTH = CqlIdentifier.fromCql("date_year_month");
    public static final int TTL = (int) DurationParser.parse(System.getProperty("mu.report.retention", "365d")).getSeconds();
    public static final CassandraModule MODULE = CassandraModule.builder()
        .table(TABLE_NAME)
        .comment("MU sent and received mail audit trail")
        .options(options -> options.withGcGraceSeconds(0))
        .statement(statement -> types -> statement
            .withPartitionKey(DATE_YEAR_MONTH, TEXT)
            .withClusteringColumn(DATE, TIMESTAMP)
            .withClusteringColumn(ID, TIMEUUID)
            .withColumn(KIND, TEXT)
            .withColumn(SENDER, TEXT)
            .withColumn(RECIPIENT, TEXT)
            .withColumn(SUBJECT, TEXT))
        .build();
    private static final String DATE_START = "date_start";
    private static final String DATE_END = "date_end";

    private final CqlSession session;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraTypesProvider typesProvider;
    private final Clock clock;
    private PreparedStatement insert;
    private PreparedStatement selectByDateBetween;

    @Inject
    public CassandraMailReportGenerator(CqlSession session,
                                        CassandraTypesProvider typesProvider,
                                        Clock clock) {
        this.session = session;
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.typesProvider = typesProvider;
        this.clock = clock;
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
            .value(DATE_YEAR_MONTH, bindMarker(DATE_YEAR_MONTH))
            .usingTtl(TTL)
            .build());
    }

    private PreparedStatement prepareSelectByDateBetween() {
        return session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(DATE_YEAR_MONTH).isEqualTo(bindMarker(DATE_YEAR_MONTH))
            .whereColumn(DATE).isGreaterThanOrEqualTo(bindMarker(DATE_START))
            .whereColumn(DATE).isLessThanOrEqualTo(bindMarker(DATE_END))
            .build());
    }

    @Override
    public Mono<Void> append(MailReportEntry entry) {
        return cassandraAsyncExecutor.executeVoid(insert.bind()
            .setString(DATE_YEAR_MONTH, YEAR_MONTH_FORMATTER.format(entry.date().atZone(clock.getZone())))
            .setUuid(ID, Uuids.timeBased())
            .setString(KIND, entry.kind().asString())
            .setString(SENDER, entry.sender().asString())
            .setString(RECIPIENT, entry.recipient().asString())
            .setString(SUBJECT, entry.subject())
            .setInstant(DATE, entry.date()));
    }

    @Override
    public Flux<MailReportEntry> generateReport(Instant start, Instant end) {
        return Flux.fromIterable(MailReportGeneratorUtils.generateYearMonthPartitions(start.atZone(clock.getZone()).toLocalDate(),
                end.atZone(clock.getZone()).toLocalDate()))
            .concatMap(yearMonth -> generateReport(yearMonth, start, end));
    }

    private Flux<MailReportEntry> generateReport(String yearMonth, Instant start, Instant end) {
        return cassandraAsyncExecutor.executeRows(selectByDateBetween.bind()
                .setString(DATE_YEAR_MONTH, yearMonth)
                .setInstant(DATE_START, start)
                .setInstant(DATE_END, end))
            .handle((row, sink) -> MailReportEntry.Kind.parse(row.getString(KIND))
                .flatMap(Throwing.function(kind -> Optional.of(new MailReportEntry(kind,
                    row.getString(SUBJECT),
                    MaybeSender.getMailSender(row.getString(SENDER)),
                    Optional.ofNullable(row.getString(RECIPIENT)).map(Throwing.function(MailAddress::new)).orElse(null),
                    row.getInstant(DATE)))))
                .ifPresent(sink::next));
    }
}