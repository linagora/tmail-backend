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
    private static final String PREFIX = "migrationProxy.";
    private static final String IMAP = "imap";
    public static final String SUBMISSION = "submission";

    private final MetricFactory metricFactory;
    private final String protocol;

    @Inject
    public MigrationProxyMetrics(MetricFactory metricFactory) {
        this(metricFactory, IMAP);
    }

    /**
     * @param protocol the proxied protocol, which scopes the metric names so that the IMAP and the SMTP
     *                 submission relays towards the same old/new backends are accounted separately
     */
    public MigrationProxyMetrics(MetricFactory metricFactory, String protocol) {
        this.metricFactory = metricFactory;
        this.protocol = protocol;
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
        return PREFIX + protocol + "." + backend.name() + "." + suffix;
    }
}
