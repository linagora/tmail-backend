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

package com.linagora.tmail.james.jmap.oidc;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Module;

public class OidcTokenCacheModuleChooser {
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcTokenCacheModuleChooser.class);

    public enum OidcTokenCacheChoice {
        MEMORY,
        REDIS;

        public static OidcTokenCacheChoice from(PropertiesProvider propertiesProvider) {
            try {
                propertiesProvider.getConfiguration("redis");
                return REDIS;
            } catch (FileNotFoundException e) {
                return MEMORY;
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Module chooseModule(OidcTokenCacheChoice oidcTokenCacheChoice) {
        switch (oidcTokenCacheChoice) {
            case REDIS -> {
                LOGGER.info("Using Redis for OIDC token storage");
                return new RedisOidcTokenCacheModule();
            }
            case MEMORY -> {
                LOGGER.info("Using Caffeine for OIDC token storage");
                return new CaffeineOidcTokenCacheModule();
            }
            default -> throw new NotImplementedException();
        }
    }
}
