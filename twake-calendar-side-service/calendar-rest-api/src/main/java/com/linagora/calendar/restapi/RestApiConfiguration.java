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

package com.linagora.calendar.restapi;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.util.Port;
import org.apache.james.utils.PropertiesProvider;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

public class RestApiConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<Port> port = Optional.empty();
        private Optional<URL> calendarSpaUrl = Optional.empty();

        private Builder() {

        }

        public Builder port(Port port) {
            this.port = Optional.of(port);
            return this;
        }

        public Builder port(Optional<Port> port) {
            this.port = port;
            return this;
        }

        public Builder calendarSpaUrl(URL url) {
            this.calendarSpaUrl = Optional.of(url);
            return this;
        }

        public Builder calendarSpaUrl(Optional<URL> url) {
            this.calendarSpaUrl = url;
            return this;
        }


        public RestApiConfiguration build() {
            try {
                return new RestApiConfiguration(port, calendarSpaUrl.orElse(new URL("https://e-calendrier.avocat.fr")));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static RestApiConfiguration parseConfiguration(PropertiesProvider propertiesProvider) throws FileNotFoundException, ConfigurationException {
        Configuration configuration = propertiesProvider.getConfiguration("configuration");
        return parseConfiguration(configuration);
    }

    public static RestApiConfiguration parseConfiguration(Configuration configuration) {
        Optional<Port> port = Optional.ofNullable(configuration.getInteger("rest.api.port", null))
            .map(Port::of);
        Optional<URL> url = Optional.ofNullable(configuration.getString("spa.calendar.url", null))
            .map(Throwing.function(URL::new));

        return RestApiConfiguration.builder()
            .port(port)
            .calendarSpaUrl(url)
            .build();
    }

    private final Optional<Port> port;
    private final URL calendarSpaUrl;

    @VisibleForTesting
    RestApiConfiguration(Optional<Port> port, URL clandarSpaUrl) {
        this.port = port;
        this.calendarSpaUrl = clandarSpaUrl;
    }

    public Optional<Port> getPort() {
        return port;
    }

    public URL getCalendarSpaUrl() {
        return calendarSpaUrl;
    }
}
