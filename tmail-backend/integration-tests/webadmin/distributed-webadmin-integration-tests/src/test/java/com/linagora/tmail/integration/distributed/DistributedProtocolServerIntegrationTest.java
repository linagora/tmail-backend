package com.linagora.tmail.integration.distributed;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.TypeName;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.FirebasePushContract;
import com.linagora.tmail.james.common.FirebaseSubscriptionProbe;
import com.linagora.tmail.james.common.FirebaseSubscriptionProbeModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;
import com.linagora.tmail.james.jmap.model.FirebaseSubscription;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionCreationRequest;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import io.restassured.RestAssured;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;

public class DistributedProtocolServerIntegrationTest {
    protected static final Domain DOMAIN = Domain.of("domain.tld");
    protected static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    protected static final Username ALICE = Username.fromLocalPartWithDomain("alice", DOMAIN);

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.ENABLED)
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule(), new FirebaseSubscriptionProbeModule())
            .overrideWith(binder -> binder.bind(FirebasePushClient.class).toInstance(FirebasePushContract.firebasePushClient())))
        .build();


    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);

        DataProbeImpl dataProbe = server.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN.asString());
        dataProbe.addUser(BOB.asString(), "password");
        dataProbe.addUser(ALICE.asString(), "password");

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setBasePath("/servers/channels")
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void disconnectShouldRevokeUserFirebaseSubscription(GuiceJamesServer server) {
        // Given a subscription
        FirebaseSubscriptionProbe firebaseSubscriptionProbe = server.getProbe(FirebaseSubscriptionProbe.class);
        FirebaseSubscription subscription = firebaseSubscriptionProbe.createSubscription(BOB, createFirebaseSubscriptionCreationRequest());

        assertThat(firebaseSubscriptionProbe.retrieveSubscription(BOB, subscription.id()))
            .isNotNull();

        // When we disconnect
        when()
            .delete("/" + BOB.asString())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        // Then the subscription should be revoked
        assertThat(firebaseSubscriptionProbe.retrieveSubscription(BOB, subscription.id()))
            .isNull();
    }

    @Test
    void disconnectShouldNotRevokeUserFirebaseSubscriptionOfUnPredicateUser(GuiceJamesServer server) {
        // Given a subscription for BOB
        FirebaseSubscriptionProbe firebaseSubscriptionProbe = server.getProbe(FirebaseSubscriptionProbe.class);
        FirebaseSubscription subscription = firebaseSubscriptionProbe.createSubscription(BOB, createFirebaseSubscriptionCreationRequest());

        assertThat(firebaseSubscriptionProbe.retrieveSubscription(BOB, subscription.id()))
            .isNotNull();

        // When we disconnect ALICE
        when()
            .delete("/" + ALICE.asString())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        // Then the subscription of BOB should still be there
        assertThat(firebaseSubscriptionProbe.retrieveSubscription(BOB, subscription.id()))
            .isNotNull();
    }

    private FirebaseSubscriptionCreationRequest createFirebaseSubscriptionCreationRequest() {
        var mockTypeName = mock(TypeName.class);
        when(mockTypeName.asString()).thenReturn("mockTypeName");

        return new FirebaseSubscriptionCreationRequest(
            "device2",
            "token2",
            OptionConverters.toScala(Optional.empty()),
            CollectionConverters.asScala(List.of(mockTypeName)).toSeq());
    }

}
