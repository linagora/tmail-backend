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

package com.linagora.tmail.blob.guice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreDataDefinition;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreExtension;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.modules.blobstore.validation.EventsourcingStorageStrategy;
import org.apache.james.modules.blobstore.validation.StorageStrategyModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableSet;

public class BlobStoreConfigurationValidationStartUpCheckTest {

    @RegisterExtension
    static final CassandraClusterExtension CASSANDRA_CLUSTER = new CassandraClusterExtension(
        CassandraDataDefinition.aggregateModules(
            CassandraSchemaVersionDataDefinition.MODULE,
            CassandraEventStoreDataDefinition.MODULE()));

    private static final Set<EventDTOModule<? extends Event, ? extends EventDTO>> EVENT_DTO_MODULES = ImmutableSet.of(StorageStrategyModule.STORAGE_STRATEGY);


    @RegisterExtension
    CassandraEventStoreExtension eventStoreExtension = new CassandraEventStoreExtension(CASSANDRA_CLUSTER,
            JsonEventSerializer.forModules(EVENT_DTO_MODULES).withoutNestedType());

    private static BlobStoreConfiguration DEDUPLICATION_STRATEGY = BlobStoreConfiguration
        .builder()
        .s3()
        .noSecondaryS3BlobStore()
        .disableCache()
        .deduplication()
        .noCryptoConfig()
        .disableSingleSave();
    private static BlobStoreConfiguration PASSTHROUGH_STRATEGY = BlobStoreConfiguration
        .builder()
        .s3()
        .noSecondaryS3BlobStore()
        .disableCache()
        .passthrough()
        .noCryptoConfig()
        .disableSingleSave();

    private EventsourcingStorageStrategy eventsourcingStorageStrategy;

    @BeforeEach
    void setUp(EventStore eventStore) {
        eventsourcingStorageStrategy = new EventsourcingStorageStrategy(eventStore);
    }

    @ParameterizedTest
    @MethodSource("storageStrategies")
    void firstStartUpShouldReturnAGoodResult(BlobStoreConfiguration blobStoreConfiguration) {
        BlobStoreConfigurationValidationStartUpCheck check = new BlobStoreConfigurationValidationStartUpCheck(blobStoreConfiguration, eventsourcingStorageStrategy);
        assertThat(check.check().getResultType()).isEqualTo(StartUpCheck.ResultType.GOOD);
    }

    @ParameterizedTest
    @MethodSource("storageStrategies")
    void startingUpTwiceWithTheStrategyShouldReturnGoodResults(BlobStoreConfiguration blobStoreConfiguration) {
        BlobStoreConfigurationValidationStartUpCheck checkFirstStartUp = new BlobStoreConfigurationValidationStartUpCheck(blobStoreConfiguration, eventsourcingStorageStrategy);
        assertThat(checkFirstStartUp.check().getResultType()).isEqualTo(StartUpCheck.ResultType.GOOD);

        BlobStoreConfigurationValidationStartUpCheck checkSecondStartUp = new BlobStoreConfigurationValidationStartUpCheck(blobStoreConfiguration, eventsourcingStorageStrategy);
        assertThat(checkSecondStartUp.check().getResultType()).isEqualTo(StartUpCheck.ResultType.GOOD);
    }

    @Test
    void startingUpWithDeduplicationThenPassthroughTheStrategyShouldReturnABadResult() {
        BlobStoreConfigurationValidationStartUpCheck checkFirstStartUp = new BlobStoreConfigurationValidationStartUpCheck(DEDUPLICATION_STRATEGY, eventsourcingStorageStrategy);
        checkFirstStartUp.check();

        BlobStoreConfigurationValidationStartUpCheck checkSecondStartUp = new BlobStoreConfigurationValidationStartUpCheck(PASSTHROUGH_STRATEGY, eventsourcingStorageStrategy);
        assertThat(checkSecondStartUp.check().getResultType()).isEqualTo(StartUpCheck.ResultType.BAD);
    }

    @Test
    void startingUpWithPassthroughThenDeduplicationTheStrategyShouldReturnAGoodResult() {
        BlobStoreConfigurationValidationStartUpCheck checkFirstStartUp = new BlobStoreConfigurationValidationStartUpCheck(PASSTHROUGH_STRATEGY, eventsourcingStorageStrategy);
        checkFirstStartUp.check();

        BlobStoreConfigurationValidationStartUpCheck checkSecondStartUp = new BlobStoreConfigurationValidationStartUpCheck(DEDUPLICATION_STRATEGY, eventsourcingStorageStrategy);
        assertThat(checkSecondStartUp.check().getResultType()).isEqualTo(StartUpCheck.ResultType.GOOD);
    }

    static Stream<Arguments> storageStrategies() {
        return Stream.of(
            Arguments.of(DEDUPLICATION_STRATEGY),
            Arguments.of(PASSTHROUGH_STRATEGY)
        );
    }
}
