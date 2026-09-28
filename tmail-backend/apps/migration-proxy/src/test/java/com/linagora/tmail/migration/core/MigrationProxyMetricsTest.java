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


package com.linagora.tmail.migration.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.Host;
import org.junit.jupiter.api.Test;

class MigrationProxyMetricsTest {
    private static final Backend OLD = new Backend("old", Host.from("old-backend", 143), false, false, false, Optional.empty());

    @Test
    void imapMetricNamesShouldBeUnchanged() {
        RecordingMetricFactory metricFactory = new RecordingMetricFactory();

        new MigrationProxyMetrics(metricFactory).recordConnection(OLD);

        assertThat(metricFactory.countFor("migrationProxy.imap.old.connections")).isEqualTo(1);
    }

    @Test
    void submissionMetricsShouldBeAccountedApartFromImapOnes() {
        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        MigrationProxyMetrics imap = new MigrationProxyMetrics(metricFactory);
        MigrationProxyMetrics submission = new MigrationProxyMetrics(metricFactory, MigrationProxyMetrics.SUBMISSION);

        submission.recordConnection(OLD);
        submission.recordBytesToBackend(OLD, 10);
        imap.recordBytesToBackend(OLD, 3);

        assertThat(metricFactory.countFor("migrationProxy.submission.old.connections")).isEqualTo(1);
        assertThat(metricFactory.countFor("migrationProxy.submission.old.bytesToBackend")).isEqualTo(10);
        assertThat(metricFactory.countFor("migrationProxy.imap.old.connections")).isZero();
        assertThat(metricFactory.countFor("migrationProxy.imap.old.bytesToBackend")).isEqualTo(3);
    }
}
