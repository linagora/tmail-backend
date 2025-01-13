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

package com.linagora.tmail.james.app;

import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;

public enum EventBusKeysChoice {
    RABBITMQ,
    REDIS;

    private static EventBusKeysChoice parse(Configuration configuration) {
        return Optional.ofNullable(configuration.getString("event.bus.keys.choice", null))
            .map(value -> EventBusKeysChoice.valueOf(value.toUpperCase(Locale.US)))
            .orElse(EventBusKeysChoice.RABBITMQ);
    }

    public static EventBusKeysChoice parse(PropertiesProvider configuration) {
        try {
            return parse(configuration.getConfiguration("queue"));
        } catch (FileNotFoundException e) {
            return RABBITMQ;
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
