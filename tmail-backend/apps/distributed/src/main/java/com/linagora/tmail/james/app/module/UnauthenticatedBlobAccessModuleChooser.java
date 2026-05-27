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

package com.linagora.tmail.james.app.module;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.linagora.tmail.james.jmap.blob.RedisUnauthenticatedBlobDownloadTokenRepositoryModule;
import com.linagora.tmail.james.jmap.blob.UnauthenticatedBlobAccessJmapModule;

public class UnauthenticatedBlobAccessModuleChooser {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnauthenticatedBlobAccessModuleChooser.class);

    public enum UnauthenticatedBlobAccessChoice {
        DISABLED,
        REDIS;

        public static UnauthenticatedBlobAccessChoice from(PropertiesProvider propertiesProvider) {
            try {
                propertiesProvider.getConfiguration("redis");
                return REDIS;
            } catch (FileNotFoundException e) {
                return DISABLED;
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Module chooseModule(UnauthenticatedBlobAccessChoice choice) {
        switch (choice) {
            case REDIS -> {
                LOGGER.info("Using Redis for JMAP unauthenticated blob access token storage");
                return Modules.combine(new UnauthenticatedBlobAccessJmapModule(), new RedisUnauthenticatedBlobDownloadTokenRepositoryModule());
            }
            case DISABLED -> {
                LOGGER.warn("JMAP unauthenticated blob access is disabled because Redis storage is not available.");
                return Modules.EMPTY_MODULE;
            }
            default -> throw new NotImplementedException();
        }
    }
}
