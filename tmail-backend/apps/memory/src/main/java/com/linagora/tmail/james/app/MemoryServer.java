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

import static com.linagora.tmail.OpenPaasModule.DavModule.CALDAV_SUPPORTED;
import static com.linagora.tmail.OpenPaasModule.DavModule.CALDAV_SUPPORT_MODULE_PROVIDER;
import static com.linagora.tmail.OpenPaasModule.OPENPAAS_INJECTION_KEY;
import static org.apache.james.JamesServerMain.LOGGER;
import static org.apache.james.MemoryJamesServerMain.JMAP;
import static org.apache.james.MemoryJamesServerMain.WEBADMIN;

import java.util.List;

import com.github.fge.lambdas.Throwing;
import com.google.inject.*;
import com.google.inject.Module;
import jakarta.inject.Named;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.ExtraProperties;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.jmap.JMAPListenerModule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.BlobMemoryModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.data.MemoryDataModule;
import org.apache.james.modules.data.MemoryDelegationStoreModule;
import org.apache.james.modules.data.MemoryDropListsModule;
import org.apache.james.modules.data.MemoryUsersRepositoryModule;
import org.apache.james.modules.eventstore.MemoryEventStoreModule;
import org.apache.james.modules.mailbox.MemoryMailboxModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.POP3ServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.memory.MemoryMailQueueModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.DropListsRoutesModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.vault.DeletedMessageVaultModule;
import org.apache.james.rate.limiter.memory.MemoryRateLimiterModule;
import org.apache.james.util.Host;

import com.google.common.collect.ImmutableList;
import com.google.inject.util.Modules;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.OpenPaasContactsConsumerModule;
import com.linagora.tmail.OpenPaasModule;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.encrypted.ClearEmailContentFactory;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.InMemoryEncryptedEmailContentStore;
import com.linagora.tmail.encrypted.InMemoryEncryptedEmailContentStoreModule;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.KeystoreMemoryModule;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.imap.TMailIMAPModule;
import com.linagora.tmail.james.app.modules.jmap.MemoryDownloadAllModule;
import com.linagora.tmail.james.app.modules.jmap.MemoryEmailAddressContactModule;
import com.linagora.tmail.james.app.modules.jmap.MemoryFirebaseSubscriptionRepositoryModule;
import com.linagora.tmail.james.app.modules.jmap.MemoryJmapSettingsRepositoryModule;
import com.linagora.tmail.james.app.modules.jmap.MemoryLabelRepositoryModule;
import com.linagora.tmail.james.app.modules.jmap.PublicAssetsMemoryModule;
import com.linagora.tmail.james.jmap.ContactSupportCapabilitiesModule;
import com.linagora.tmail.james.jmap.TMailJMAPModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseCommonModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.mail.TMailMailboxSortOrderProviderModule;
import com.linagora.tmail.james.jmap.method.CalendarEventMethodModule;
import com.linagora.tmail.james.jmap.method.ContactAutocompleteMethodModule;
import com.linagora.tmail.james.jmap.method.CustomMethodModule;
import com.linagora.tmail.james.jmap.method.EmailRecoveryActionMethodModule;
import com.linagora.tmail.james.jmap.method.EmailSendMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.FilterGetMethodModule;
import com.linagora.tmail.james.jmap.method.FilterSetMethodModule;
import com.linagora.tmail.james.jmap.method.ForwardGetMethodModule;
import com.linagora.tmail.james.jmap.method.ForwardSetMethodModule;
import com.linagora.tmail.james.jmap.method.JmapSettingsMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;
import com.linagora.tmail.james.jmap.method.LabelMethodModule;
import com.linagora.tmail.james.jmap.method.MailboxClearMethodModule;
import com.linagora.tmail.james.jmap.method.MessageVaultCapabilitiesModule;
import com.linagora.tmail.james.jmap.oidc.WebFingerModule;
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetsModule;
import com.linagora.tmail.james.jmap.routes.DownloadAllRoutesModule;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModule;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.team.mailboxes.TeamMailboxJmapModule;
import com.linagora.tmail.james.jmap.ticket.TicketRoutesModule;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingModule;
import com.linagora.tmail.team.TMailScanningQuotaSearcherModule;
import com.linagora.tmail.team.TeamMailboxModule;
import com.linagora.tmail.webadmin.EmailAddressContactRoutesModule;
import com.linagora.tmail.webadmin.RateLimitPlanRoutesModule;
import com.linagora.tmail.webadmin.TeamMailboxRoutesModule;
import com.linagora.tmail.webadmin.archival.InboxArchivalTaskModule;
import com.linagora.tmail.webadmin.cleanup.MailboxesCleanupModule;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingModule;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.ExtendedClassLoader;
import org.apache.james.utils.GuiceGenericLoader;
import org.apache.james.utils.NamingScheme;

public class MemoryServer {
    public static final Module IN_MEMORY_SERVER_MODULE = Modules.combine(
        new MailetProcessingModule(),
        new BlobMemoryModule(),
        new DeletedMessageVaultModule(),
        new BlobExportMechanismModule(),
        new MailboxModule(),
        new MemoryDataModule(),
        new MemoryDelegationStoreModule(),
        new MemoryEventStoreModule(),
        new MemoryMailboxModule(),
        new MemoryMailQueueModule(),
        new TaskManagerModule());

    public static final Module PROTOCOLS = Modules.combine(
        new IMAPServerModule(),
        new ManageSieveServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule());

    public static final Module JMAP_LINAGORA = Modules.override(
        JMAP,
        new TMailJMAPModule(),
        new CalendarEventMethodModule(),
        new ContactAutocompleteMethodModule(),
        new ContactIndexingModule(),
        new CustomMethodModule(),
        new EncryptedEmailDetailedViewGetMethodModule(),
        new EncryptedEmailFastViewGetMethodModule(),
        new EmailSendMethodModule(),
        new FilterGetMethodModule(),
        new FilterSetMethodModule(),
        new ForwardGetMethodModule(),
        new InMemoryEncryptedEmailContentStoreModule(),
        new KeystoreMemoryModule(),
        new ForwardSetMethodModule(),
        new KeystoreSetMethodModule(),
        new KeystoreGetMethodModule(),
        new TicketRoutesModule(),
        new WebFingerModule(),
        new EmailRecoveryActionMethodModule(),
        new LabelMethodModule(),
        new JmapSettingsMethodModule(),
        new PublicAssetsModule(),
        new PublicAssetsMemoryModule(),
        new MessageVaultCapabilitiesModule(),
        new MailboxesCleanupModule(),
        new InboxArchivalTaskModule(),
        new ContactSupportCapabilitiesModule(),
        new DownloadAllRoutesModule(),
        new MailboxClearMethodModule())
        .with(new TeamMailboxJmapModule());

    public static final Module MODULES = Modules.override(
          IN_MEMORY_SERVER_MODULE,
          PROTOCOLS,
          JMAP_LINAGORA,
          WEBADMIN,
          new TeamMailboxModule(),
          new TeamMailboxRoutesModule(),
          new DKIMMailetModule())
        .with(new TeamMailboxModule(),
            new TMailScanningQuotaSearcherModule(),
            new MemoryRateLimiterModule(),
            new MemoryRateLimitingModule(),
            new RateLimitPlanRoutesModule(),
            new MemoryEmailAddressContactModule(),
            new EmailAddressContactRoutesModule(),
            new MemoryLabelRepositoryModule(),
            new MemoryJmapSettingsRepositoryModule(),
            new PublicAssetsMemoryModule(),
            new TMailMailboxSortOrderProviderModule(),
            new TMailIMAPModule(),
            new MemoryDownloadAllModule());

    public static void main(String[] args) throws Exception {
        MemoryConfiguration configuration = MemoryConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .combineWith(new JMXServerModule());

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(MemoryConfiguration configuration) {
        ExtraProperties.initialize();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(MODULES)
            .combineWith(new UsersRepositoryModuleChooser(new MemoryUsersRepositoryModule())
                .chooseModules(configuration.usersRepositoryImplementation()))
            .combineWith(chooseFirebase(configuration.firebaseModuleChooserConfiguration()))
            .combineWith(chooseLinagoraServiceDiscovery(configuration.linagoraServicesDiscoveryModuleChooserConfiguration()))
            .combineWith(choosePop3ServerModule(configuration))
            .combineWith(chooseDropListsModule(configuration))
            .combineWith(extentionModules(configuration))
            .overrideWith(chooseOpenPaas(configuration.openPaasModuleChooserConfiguration()))
            .overrideWith(chooseMailbox(configuration.mailboxConfiguration()))
            .overrideWith(chooseJmapModule(configuration));
    }

    private static Module extentionModules(MemoryConfiguration configuration) {
        Injector injector = Guice.createInjector();
        ExtendedClassLoader extendedClassLoader = configuration.extendedClassLoader();
        Module additionalExtensionBindings = Modules.combine(configuration.extentionConfiguration().getAdditionalGuiceModulesForExtensions()
            .stream()
            .map(Throwing.<ClassName, Module>function(className -> instanciate(injector, NamingScheme.IDENTITY, extendedClassLoader, className)))
            .peek(module -> LOGGER.info("Enabling injects contained in " + module.getClass().getCanonicalName()))
            .collect(ImmutableList.toImmutableList()));
        return additionalExtensionBindings;
    }

    private static <T> T instanciate(Injector injector, NamingScheme namingScheme, ExtendedClassLoader extendedClassLoader, ClassName className) throws ClassNotFoundException {

        try {
            ImmutableList<Class<T>> classes = namingScheme.<T>toFullyQualifiedClassNames(className)
                .flatMap(fullyQualifiedName -> extendedClassLoader.<T>locateClass(fullyQualifiedName).stream())
                .collect(ImmutableList.toImmutableList());

            if (classes.isEmpty()) {
                throw new ClassNotFoundException(className.getName());
            }
            if (classes.size() > 1) {
                LOGGER.warn("Ambiguous class name for {}. Corresponding classes are {} and {} will be loaded",
                    className, classes, classes.get(0));
            }
            return injector.getInstance(classes.get(0));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + className.getName(), e);
        }
    }


    private static Module chooseJmapModule(MemoryConfiguration configuration) {
        if (configuration.jmapEnabled()) {
            return new JMAPListenerModule();
        }
        return binder -> {
        };
    }

    private static class EncryptedMailboxModule extends AbstractModule {
        @Provides
        @Singleton
        MailboxManager provide(InMemoryMailboxManager mailboxManager, KeystoreManager keystoreManager,
                               ClearEmailContentFactory clearEmailContentFactory,
                               InMemoryEncryptedEmailContentStore contentStore) {
            return new EncryptedMailboxManager(mailboxManager, keystoreManager, clearEmailContentFactory, contentStore);
        }
    }

    private static List<Module> chooseMailbox(MailboxConfiguration mailboxConfiguration) {
        if (mailboxConfiguration.isEncryptionEnabled()) {
            return ImmutableList.of(new EncryptedMailboxModule());
        }
        return ImmutableList.of();
    }

    private static List<Module> chooseFirebase(FirebaseModuleChooserConfiguration moduleChooserConfiguration) {
        if (moduleChooserConfiguration.enable()) {
            return List.of(new MemoryFirebaseSubscriptionRepositoryModule(), new FirebaseCommonModule());
        }
        return List.of();
    }

    private static List<Module> chooseLinagoraServiceDiscovery(LinagoraServicesDiscoveryModuleChooserConfiguration moduleChooserConfiguration) {
        if (moduleChooserConfiguration.enable()) {
            return List.of(new LinagoraServicesDiscoveryModule());
        }
        return List.of();
    }

    private static List<Module> chooseOpenPaas(OpenPaasModuleChooserConfiguration moduleChooserConfiguration) {
        if (moduleChooserConfiguration.enabled()) {
            ImmutableList.Builder<Module> moduleBuilder = ImmutableList.<Module>builder().add(new OpenPaasModule());

            if (moduleChooserConfiguration.shouldEnableDavServerInteraction()) {
                moduleBuilder.add(new OpenPaasModule.DavModule());
            }
            moduleBuilder.add(CALDAV_SUPPORT_MODULE_PROVIDER.apply(moduleChooserConfiguration.shouldEnableDavServerInteraction()));
            if (moduleChooserConfiguration.contactsConsumerEnabled()) {
                moduleBuilder.add(Modules.override(new OpenPaasContactsConsumerModule())
                    .with(new AbstractModule() {
                        @Provides
                        @Named(OPENPAAS_INJECTION_KEY)
                        @Singleton
                        public RabbitMQConfiguration provideRabbitMQConfiguration(OpenPaasConfiguration openPaasConfiguration) {
                            List<AmqpUri> uris = openPaasConfiguration.contactConsumerConfiguration().get().amqpUri();
                            return uris.getFirst()
                                .toRabbitMqConfiguration()
                                .hosts(uris.stream().map(uri -> Host.from(uri.getUri().getHost(), uri.getPort())).collect(ImmutableList.toImmutableList()))
                                .build();
                        }
                    }));
            }
            return moduleBuilder.build();
        }
        return List.of(CALDAV_SUPPORT_MODULE_PROVIDER.apply(!CALDAV_SUPPORTED));
    }

    private static Module chooseDropListsModule(MemoryConfiguration configuration) {
        if (configuration.dropListEnabled()) {
            return Modules.combine(new MemoryDropListsModule(), new DropListsRoutesModule());
        }
        return binder -> {

        };
    }

    private static Module choosePop3ServerModule(MemoryConfiguration configuration) {
        try {
            if (CollectionUtils.isNotEmpty(configuration.fileConfigurationProvider().getConfiguration("pop3server").configurationsAt("pop3server"))) {
                return new POP3ServerModule();
            }
            return Modules.EMPTY_MODULE;
        } catch (ConfigurationException exception) {
            return Modules.EMPTY_MODULE;
        }
    }
}
