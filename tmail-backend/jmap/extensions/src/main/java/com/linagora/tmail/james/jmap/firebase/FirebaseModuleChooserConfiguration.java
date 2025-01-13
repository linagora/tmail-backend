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

package com.linagora.tmail.james.jmap.firebase;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;

public record FirebaseModuleChooserConfiguration(boolean enable) {
    public static final FirebaseModuleChooserConfiguration ENABLED = new FirebaseModuleChooserConfiguration(true);
    public static final FirebaseModuleChooserConfiguration DISABLED = new FirebaseModuleChooserConfiguration(false);

    public static FirebaseModuleChooserConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("firebase");
            return new FirebaseModuleChooserConfiguration(configuration.getBoolean("enable", true));
        } catch (FileNotFoundException e) {
            return new FirebaseModuleChooserConfiguration(false);
        }
    }
}
