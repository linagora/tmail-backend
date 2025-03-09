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

package com.linagora.calendar.app;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.rules.TemporaryFolder;

public class TwakeCalendarExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private final TwakeCalendarConfiguration.Builder configuration;

    private TwakeCalendarGuiceServer server;
    private TemporaryFolder temporaryFolder = new TemporaryFolder();

    public TwakeCalendarExtension(TwakeCalendarConfiguration.Builder configuration) {
        this.configuration = configuration;
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        temporaryFolder.create();
        server = TwakeCalendarMain.createServer(configuration.workingDirectory(temporaryFolder.newFolder()).build());
        server.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext){
        temporaryFolder.delete();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == TwakeCalendarGuiceServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return server;
    }
}
