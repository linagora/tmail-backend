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

package com.linagora.tmail;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.rabbitmq.client.ConnectionFactory;

/**
 * The `AmqpUri` class represents an AMQP (Advanced Message Queuing Protocol) URI, which
 * is used to configure a connection to a RabbitMQ message broker.
 *<p>
 * This class handles the parsing and validation of the AMQP URI, and provides methods to extract
 * relevant information from the URI, such as the username, password, and virtual host.
 *<p>
 * Notes:
 * <ul>
 *   <li>Providing an AMQP URI without a username will default to 'guest' as the username.</li>
 *   <li>Providing an AMQP URI without a password will default to 'guest' as the password.</li>
 *   <li>Providing an AMQP URI without a port will use <code>5672</code></co> as the port.</li>
 *   <li>Providing an AMQPS (AMQP over SSL) URI without a port will use <code>5671</code></co> as the port.</li>
 * </ul>
 */
public final class AmqpUri {
    private final URI uri;
    private final AmqpUserInfo userInfo;
    private final Optional<String> vhost;
    private final int port;

    /**
     * Constructs an `AmqpUri` object from the provided `URI`.
     *
     * @param uri the AMQP URI to be parsed and validated
     * @throws RuntimeException if the provided URI is invalid
     */
    public AmqpUri(URI uri) {
        Preconditions.checkNotNull(uri);
        ConnectionFactory connectionFactory = new ConnectionFactory();
        try {
            connectionFactory.setUri(uri);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(String.format("Invalid AmqpUri: '%s'", uri), e);
        }

        this.uri = uri;
        port = connectionFactory.getPort();
        userInfo = new AmqpUserInfo(connectionFactory.getUsername(), connectionFactory.getPassword());
        vhost = Optional.ofNullable(connectionFactory.getVirtualHost());
    }

    /**
     * Creates an `AmqpUri` object from the provided `URI`.
     *
     * @param uri the AMQP URI to be used
     * @return an `AmqpUri` object representing the provided URI
     */
    public static AmqpUri from(URI uri) {
        return new AmqpUri(uri);
    }

    /**
     * Creates an `AmqpUri` object from the provided AMQP URI string.
     *
     * @param uri the AMQP URI string to be used
     * @return an `AmqpUri` object representing the provided URI string
     */
    public static AmqpUri from(String uri) {
        return new AmqpUri(URI.create(uri));
    }

    /**
     * An `amqps://` URI must be connected with `useSsl`: otherwise the RabbitMQ client enables TLS on its own with a
     * trust manager that accepts any certificate and no hostname verification.
     */
    public static boolean isAmqps(URI uri) {
        return "amqps".equalsIgnoreCase(uri.getScheme());
    }

    /**
     * Returns an `Optional` containing the current `AmqpUri` object.
     *
     * @return an `Optional` containing the `AmqpUri` object
     */
    public Optional<AmqpUri> asOptional() {
        return Optional.of(this);
    }

    /**
     * Returns the AMQP user information, including the username and password,
     * extracted from the AMQP URI.
     *
     * @return an `AmqpUserInfo` object containing the username and password
     */
    public AmqpUserInfo getUserInfo() {
        return userInfo;
    }

    /**
     * Returns the virtual host specified in the AMQP URI, if any.
     *
     * @return an `Optional` containing the virtual host, or an empty `Optional` if not specified
     */
    public Optional<String> getVhost() {
        return vhost;
    }

    /**
     * Returns the port number specified in the AMQP URI.
     * <p>
     * If the port is not specified in the URI it falls back to RabbitMQ defaults:
     * <lu>
     *     <li>AMQP  --> 6572</li>
     *     <li>AMQPS --> 6571</li>
     * </lu>
     *
     * @return the port number from the AMQP URI
     */
    public int getPort() {
        return port;
    }

    /**
     * Converts the `AmqpUri` object into a `RabbitMQConfiguration` object, which can be used to
     * configure the RabbitMQ client.
     *
     * @return a `RabbitMQConfiguration` object representing the AMQP URI
     */
    public RabbitMQConfiguration.Builder toRabbitMqConfiguration(RabbitMQConfiguration commonRabbitMQConfiguration) {
        return toRabbitMqConfiguration(commonRabbitMQConfiguration, Optional.empty());
    }

    /**
     * The management API is called with the credentials of this AMQP URI: it must be the one of this server. Without
     * an explicit management URI, the management URI of the common configuration is moved to the host of this AMQP URI.
     */
    public RabbitMQConfiguration.Builder toRabbitMqConfiguration(RabbitMQConfiguration commonRabbitMQConfiguration,
                                                                 Optional<URI> managementUri) {
        return RabbitMQConfiguration.builder()
            .amqpUri(uri)
            .managementUri(managementUri.orElseGet(() -> withHost(commonRabbitMQConfiguration.getManagementUri(), getHost())))
            .managementCredentials(userInfo.asManagementCredentials())
            .vhost(getVhost())
            .useSsl(commonRabbitMQConfiguration.useSsl() || isAmqps(uri))
            .useSslManagement(commonRabbitMQConfiguration.useSslManagement())
            .sslConfiguration(commonRabbitMQConfiguration.getSslConfiguration())
            .useQuorumQueues(commonRabbitMQConfiguration.isQuorumQueuesUsed())
            .quorumQueueReplicationFactor(commonRabbitMQConfiguration.getQuorumQueueReplicationFactor())
            .quorumQueueDeliveryLimit(commonRabbitMQConfiguration.getQuorumQueueDeliveryLimit())
            .networkRecoveryIntervalInMs(commonRabbitMQConfiguration.getNetworkRecoveryIntervalInMs())
            .queueTTL(commonRabbitMQConfiguration.getQueueTTL());
    }

    public RabbitMQConfiguration.Builder toRabbitMqConfiguration() {
        return RabbitMQConfiguration.builder()
            .amqpUri(uri)
            .managementUri(uri)
            .managementCredentials(userInfo.asManagementCredentials())
            .vhost(getVhost())
            .useSsl(isAmqps(uri));
    }

    public URI getUri() {
        return uri;
    }

    /**
     * Returns the host of the AMQP URI, defaulting as the RabbitMQ client does when it is not specified.
     */
    public String getHost() {
        return Optional.ofNullable(uri.getHost()).orElse(ConnectionFactory.DEFAULT_HOST);
    }

    // Drops the user information, which would belong to another server. Keeps the raw path, query and fragment, so
    // that their escaped characters stay escaped.
    private static URI withHost(URI uri, String host) {
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        String path = Optional.ofNullable(uri.getRawPath()).orElse("");
        String query = Optional.ofNullable(uri.getRawQuery()).map(rawQuery -> "?" + rawQuery).orElse("");
        String fragment = Optional.ofNullable(uri.getRawFragment()).map(rawFragment -> "#" + rawFragment).orElse("");
        try {
            return new URI(uri.getScheme() + "://" + host + port + path + query + fragment);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot build the management URI of host " + host, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AmqpUri amqpUri)) {
            return false;
        }
        return Objects.equals(uri, amqpUri.uri)
            && Objects.equals(userInfo, amqpUri.userInfo)
            && Objects.equals(vhost, amqpUri.vhost)
            && Objects.equals(port, amqpUri.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, userInfo, vhost, port);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("amqpURI", uri)
            .add("userInfo", userInfo)
            .add("vhost", vhost)
            .add("port", port)
            .toString();
    }
}