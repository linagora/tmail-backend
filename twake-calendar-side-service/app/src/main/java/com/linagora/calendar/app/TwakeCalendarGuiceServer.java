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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.apache.james.ConfigurationSanitizingPerformer;
import org.apache.james.IsStartedProbe;
import org.apache.james.StartUpChecksPerformer;
import org.apache.james.modules.IsStartedProbeModule;
import org.apache.james.onami.lifecycle.Stager;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.GuiceProbeProvider;
import org.apache.james.utils.InitializationOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;

public class TwakeCalendarGuiceServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TwakeCalendarGuiceServer.class);
    private static final boolean SHOULD_EXIT_ON_STARTUP_ERROR = Boolean.valueOf(System.getProperty("james.exit.on.startup.error", "true"));

    protected final Module module;
    private final IsStartedProbe isStartedProbe;
    private Stager<PreDestroy> preDestroy;
    private GuiceProbeProvider guiceProbeProvider;

    public static TwakeCalendarGuiceServer forConfiguration(Configuration configuration) {
        IsStartedProbe isStartedProbe = new IsStartedProbe();

        return new TwakeCalendarGuiceServer(
            isStartedProbe,
            Modules.combine(
                new IsStartedProbeModule(isStartedProbe),
                new TwakeCalendarCommonServicesModule(configuration)));
    }

    protected TwakeCalendarGuiceServer(IsStartedProbe isStartedProbe, Module module) {
        this.isStartedProbe = isStartedProbe;
        this.module = module;
    }
    
    public TwakeCalendarGuiceServer combineWith(Module... modules) {
        return combineWith(Arrays.asList(modules));
    }

    public TwakeCalendarGuiceServer combineWith(Collection<Module> modules) {
        return new TwakeCalendarGuiceServer(isStartedProbe, Modules.combine(Iterables.concat(Arrays.asList(module), modules)));
    }

    public TwakeCalendarGuiceServer overrideWith(Module... overrides) {
        return new TwakeCalendarGuiceServer(isStartedProbe, Modules.override(module).with(overrides));
    }

    public TwakeCalendarGuiceServer overrideWith(List<Module> overrides) {
        return new TwakeCalendarGuiceServer(isStartedProbe, Modules.override(module).with(overrides));
    }

    public void start() throws Exception {
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            Injector injector = Guice.createInjector(module);
            guiceProbeProvider = injector.getInstance(GuiceProbeProvider.class);
            preDestroy = injector.getInstance(Key.get(new TypeLiteral<Stager<PreDestroy>>() {
            }));
            injector.getInstance(ConfigurationSanitizingPerformer.class).sanitize();
            injector.getInstance(InitializationOperations.class).initModules();
            injector.getInstance(StartUpChecksPerformer.class).performCheck();
            isStartedProbe.notifyStarted();
            LOGGER.info("JAMES server started in: {}ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        } catch (Throwable e) {
            LOGGER.error("Fatal error while starting James", e);
            if (SHOULD_EXIT_ON_STARTUP_ERROR) {
                System.exit(1);
            } else {
                throw e;
            }
        }
    }

    public void stop() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        LOGGER.info("Stopping JAMES server");
        isStartedProbe.notifyStoped();
        if (preDestroy != null) {
            preDestroy.stage();
        }
        LOGGER.info("JAMES server stopped in: {}ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    public boolean isStarted() {
        return isStartedProbe.isStarted();
    }

    public <T extends GuiceProbe> T getProbe(Class<T> probe) {
        return guiceProbeProvider.getProbe(probe);
    }
}
