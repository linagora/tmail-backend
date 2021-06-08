/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailetcontainer.impl;

import static org.apache.james.modules.server.CamelMailetContainerModule.BCC_Check;

import java.util.Arrays;
import java.util.Set;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailetcontainer.AutomaticallySentMailDetectorImpl;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.james.mailetcontainer.api.MatcherLoader;
import org.apache.james.mailetcontainer.api.jmx.MailSpoolerMBean;
import org.apache.james.mailetcontainer.impl.camel.CamelCompositeProcessor;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.modules.server.CamelMailetContainerModule.DefaultProcessorsConfigurationSupplier;
import org.apache.james.modules.server.CamelMailetContainerModule.ProcessorsCheck;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.GuiceMailetLoader;
import org.apache.james.utils.GuiceMatcherLoader;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.MailetConfigurationOverride;
import org.apache.james.utils.SpoolerProbe;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableListMultimap;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class MailetContainerImplModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailetContainerImplModule.class);

    @Override
    protected void configure() {
        bind(CompositeProcessorImpl.class).in(Scopes.SINGLETON);
        bind(MailProcessor.class).to(CompositeProcessorImpl.class);

        bind(JamesMailSpooler.class).in(Scopes.SINGLETON);
        bind(MailSpoolerMBean.class).to(JamesMailSpooler.class);

        bind(JamesMailetContext.class).in(Scopes.SINGLETON);
        bind(MailetContext.class).to(JamesMailetContext.class);

        bind(AutomaticallySentMailDetectorImpl.class).in(Scopes.SINGLETON);
        bind(AutomaticallySentMailDetector.class).to(AutomaticallySentMailDetectorImpl.class);

        bind(MailetLoader.class).to(GuiceMailetLoader.class);
        bind(MatcherLoader.class).to(GuiceMatcherLoader.class);

        Multibinder.newSetBinder(binder(), MailetConfigurationOverride.class);
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(SpoolerProbe.class);
        Multibinder<InitializationOperation> initialisationOperations = Multibinder.newSetBinder(binder(), InitializationOperation.class);
        initialisationOperations.addBinding().to(MailetModuleInitializationOperation.class);

        Multibinder<ProcessorsCheck> transportProcessorChecks = Multibinder.newSetBinder(binder(), ProcessorsCheck.class);
        transportProcessorChecks.addBinding().toInstance(BCC_Check);
    }

    @Provides
    @Singleton
    public JamesMailSpooler.Configuration spoolerConfiguration(MailRepositoryStore mailRepositoryStore, ConfigurationProvider configurationProvider) {
        HierarchicalConfiguration<ImmutableNode> conf = getJamesSpoolerConfiguration(configurationProvider);
        return JamesMailSpooler.Configuration.from(mailRepositoryStore, conf);
    }

    private HierarchicalConfiguration<ImmutableNode> getJamesSpoolerConfiguration(ConfigurationProvider configurationProvider) {
        try {
            return configurationProvider.getConfiguration("mailetcontainer")
                .configurationAt("spooler");
        } catch (Exception e) {
            LOGGER.warn("Could not locate configuration for James Spooler. Assuming empty configuration for this component.");
            return new BaseHierarchicalConfiguration();
        }
    }

    @ProvidesIntoSet
    InitializationOperation initMailetContext(ConfigurationProvider configurationProvider, JamesMailetContext mailetContext) {
        return InitilizationOperationBuilder
            .forClass(JamesMailetContext.class)
            .init(() -> mailetContext.configure(getMailetContextConfiguration(configurationProvider)));
    }

    @VisibleForTesting
    HierarchicalConfiguration<ImmutableNode> getMailetContextConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
        HierarchicalConfiguration<ImmutableNode> mailetContainerConfiguration = configurationProvider.getConfiguration("mailetcontainer");
        try {
            return mailetContainerConfiguration.configurationAt("context");
        } catch (ConfigurationRuntimeException e) {
            LOGGER.warn("Could not locate configuration for Mailet context. Assuming empty configuration for this component.");
            return new BaseHierarchicalConfiguration();
        }
    }

    @Singleton
    public static class MailetModuleInitializationOperation implements InitializationOperation {
        private final ConfigurationProvider configurationProvider;
        private final CompositeProcessorImpl compositeProcessor;
        private final DefaultProcessorsConfigurationSupplier defaultProcessorsConfigurationSupplier;
        private final Set<ProcessorsCheck> processorsCheckSet;
        private final DefaultCamelContext camelContext;
        private final JamesMailSpooler jamesMailSpooler;
        private final JamesMailSpooler.Configuration spoolerConfiguration;

        @Inject
        public MailetModuleInitializationOperation(ConfigurationProvider configurationProvider,
                                                   CompositeProcessorImpl compositeProcessor,
                                                   Set<ProcessorsCheck> processorsCheckSet,
                                                   DefaultProcessorsConfigurationSupplier defaultProcessorsConfigurationSupplier,
                                                   DefaultCamelContext camelContext, JamesMailSpooler jamesMailSpooler, JamesMailSpooler.Configuration spoolerConfiguration) {
            this.configurationProvider = configurationProvider;
            this.compositeProcessor = compositeProcessor;
            this.processorsCheckSet = processorsCheckSet;
            this.defaultProcessorsConfigurationSupplier = defaultProcessorsConfigurationSupplier;
            this.camelContext = camelContext;
            this.jamesMailSpooler = jamesMailSpooler;
            this.spoolerConfiguration = spoolerConfiguration;
        }

        @Override
        public void initModule() throws Exception {
            configureProcessors(camelContext);
            checkProcessors();
        }

        private void configureProcessors(DefaultCamelContext camelContext) throws Exception {
            compositeProcessor.configure(getProcessorConfiguration());
            compositeProcessor.init();

            jamesMailSpooler.configure(spoolerConfiguration);
            jamesMailSpooler.init();
        }

        @VisibleForTesting
        HierarchicalConfiguration<ImmutableNode> getProcessorConfiguration() throws ConfigurationException {
            HierarchicalConfiguration<ImmutableNode> mailetContainerConfiguration = configurationProvider.getConfiguration("mailetcontainer");
            try {
                return mailetContainerConfiguration.configurationAt("processors");
            } catch (ConfigurationRuntimeException e) {
                LOGGER.warn("Could not load configuration for Processors. Fallback to default.");
                return defaultProcessorsConfigurationSupplier.getDefaultConfiguration();
            }
        }

        private void checkProcessors() throws ConfigurationException {
            ImmutableListMultimap<String, MatcherMailetPair> processors = Arrays.stream(compositeProcessor.getProcessorStates())
                .flatMap(state -> {
                    MailProcessor processor = compositeProcessor.getProcessor(state);
                    if (processor instanceof MailetProcessorImpl) {
                        MailetProcessorImpl camelProcessor = (MailetProcessorImpl) processor;
                        return camelProcessor.getPairs().stream()
                            .map(pair -> Pair.of(state, pair));
                    } else {
                        throw new RuntimeException("Can not perform checks as transport processor is not an instance of " + MailProcessor.class);
                    }
                })
                .collect(Guavate.toImmutableListMultimap(
                    Pair::getKey,
                    Pair::getValue));
            for (ProcessorsCheck check : processorsCheckSet) {
                check.check(processors);
            }
        }

        @Override
        public Class<? extends Startable> forClass() {
            return CamelCompositeProcessor.class;
        }
    }

}
