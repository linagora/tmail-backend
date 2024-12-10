package com.linagora.tmail.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.api.MailReportGenerator;
import com.linagora.tmail.api.MailReportGeneratorContract;

public class CassandraMailReportGeneratorTest implements MailReportGeneratorContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMailReportGenerator.MODULE);

    private CassandraMailReportGenerator cassandraMailReportGenerator;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        cassandraMailReportGenerator = new CassandraMailReportGenerator(cassandra.getConf(),
            cassandra.getTypesProvider());
        cassandraMailReportGenerator.start();
    }

    @Override
    public MailReportGenerator testee() {
        return cassandraMailReportGenerator;
    }
}
