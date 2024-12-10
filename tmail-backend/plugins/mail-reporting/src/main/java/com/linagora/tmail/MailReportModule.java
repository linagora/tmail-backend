package com.linagora.tmail;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.linagora.tmail.api.MailReportGenerator;
import com.linagora.tmail.cassandra.CassandraMailReportGenerator;

public class MailReportModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CassandraMailReportGenerator.class).in(Scopes.SINGLETON);
        bind(MailReportGenerator.class).to(CassandraMailReportGenerator.class);
    }
}
