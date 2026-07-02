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

import jakarta.inject.Inject;

import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;

/**
 * Exposes proxy statistics: connection volumes, bytes relayed in both directions and backend
 * response times, broken down per backend (old/new).
 */
public class MigrationProxyMetrics {
    private static final String PREFIX = "migrationProxy.imap";

    private final MetricFactory metricFactory;

    @Inject
    public MigrationProxyMetrics(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    public void recordConnection(Backend backend) {
        counter(backend, "connections").increment();
    }

    public void recordBytesToBackend(Backend backend, int bytes) {
        counter(backend, "bytesToBackend").add(bytes);
    }

    public void recordBytesToClient(Backend backend, int bytes) {
        counter(backend, "bytesToClient").add(bytes);
    }

    public TimeMetric backendResponseTimer(Backend backend) {
        return metricFactory.timer(name(backend, "backendResponseTime"));
    }

    private Metric counter(Backend backend, String suffix) {
        return metricFactory.generate(name(backend, suffix));
    }

    private String name(Backend backend, String suffix) {
        return PREFIX + "." + backend.name() + "." + suffix;
    }
}
