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

package com.linagora.tmail.mailet.rag;

import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.inject.AbstractModule;

public class RagListenerConfiguration extends AbstractModule {
    private final Optional<List<Object>> whiteList;


    public RagListenerConfiguration(Optional<List<Object>> whiteList) {
        this.whiteList = whiteList;
    }

    public static RagListenerConfiguration from(Configuration configuration) throws IllegalArgumentException {
       Optional<List<Object>> whiteListParam = Optional.ofNullable(configuration.getList("users",null));

       return new RagListenerConfiguration(whiteListParam);
    }

    public Optional<List<Object>> getWhitelist() {
        return this.whiteList;
    }
}