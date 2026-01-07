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

package com.linagora.tmail.james;

import static com.linagora.tmail.blob.guice.BlobStoreModulesChooser.MAYBE_SECONDARY_BLOBSTORE;
import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.requestSpecification;
import static io.restassured.RestAssured.with;
import static io.restassured.http.ContentType.JSON;
import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.TEN_SECONDS;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.http.HttpStatus;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.Group;
import org.apache.james.jmap.JMAPUrls;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.EventDeadLettersRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.blob.guice.SecondaryS3BlobStoreConfiguration;
import com.linagora.tmail.blob.secondaryblobstore.FailedBlobEvents;
import com.linagora.tmail.blob.secondaryblobstore.FailedBlobOperationListener;
import com.linagora.tmail.blob.secondaryblobstore.SecondaryBlobStoreDAO;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedEncryptedMailboxModule;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.EncryptHelper;
import com.linagora.tmail.james.common.LinagoraEmailSendMethodContract$;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import io.restassured.authentication.PreemptiveBasicAuthScheme;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Tag(BasicFeature.TAG)
class DistributedLinagoraSecondaryBlobStoreTest {
    public static final ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and()
        .with()
        .pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    static class BlobStoreProbe implements GuiceProbe {
        private final S3BlobStoreDAO primaryBlobStoreDAO;
        private final S3BlobStoreDAO secondaryBlobStoreDAO;
        private final EventDeadLetters eventDeadLetters;

        @Inject
        public BlobStoreProbe(@Named(MAYBE_SECONDARY_BLOBSTORE) BlobStoreDAO blobStoreDAO,
                              EventDeadLetters eventDeadLetters) {
            SecondaryBlobStoreDAO secondaryBlobStoreDAO = (SecondaryBlobStoreDAO) blobStoreDAO;
            this.primaryBlobStoreDAO = (S3BlobStoreDAO) secondaryBlobStoreDAO.getFirstBlobStoreDAO();
            this.secondaryBlobStoreDAO = (S3BlobStoreDAO) secondaryBlobStoreDAO.getSecondBlobStoreDAO();
            this.eventDeadLetters = eventDeadLetters;
        }

        public S3BlobStoreDAO getPrimaryBlobStoreDAO() {
            return primaryBlobStoreDAO;
        }

        public S3BlobStoreDAO getSecondaryBlobStoreDAO() {
            return secondaryBlobStoreDAO;
        }

        public EventDeadLetters getEventDeadLetters() {
            return eventDeadLetters;
        }
    }

    static final Domain DOMAIN = Domain.of("domain.tld");
    static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    static final Username ANDRE = Username.fromLocalPartWithDomain("andre", DOMAIN);
    static final String BOB_PASSWORD = "bobpassword";
    static final String ANDRE_PASSWORD = "andrepassword";
    static final String ACCEPT_RFC8621_VERSION_HEADER = "application/json; jmapVersion=rfc-8621";
    static final String ACCOUNT_ID = "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6";
    static final String SECONDARY_BUCKET_SUFFIX = "-secondary-bucket-suffix";

    static DockerAwsS3Container secondaryS3 = new DockerAwsS3Container();
    static SecondaryS3BlobStoreConfiguration secondaryS3Configuration;

    @RegisterExtension
    static JamesServerExtension
        testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.scanning())
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .secondaryS3BlobStore(secondaryS3Configuration)
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.RABBITMQ)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new DistributedEncryptedMailboxModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(BlobStoreProbe.class)))
        .build();

    @BeforeAll
    static void beforeAll() {
        secondaryS3.start();
        AwsS3AuthConfiguration authConfiguration = AwsS3AuthConfiguration.builder()
            .endpoint(secondaryS3.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();

        secondaryS3Configuration = new SecondaryS3BlobStoreConfiguration(S3BlobStoreConfiguration.builder()
            .authConfiguration(authConfiguration)
            .region(secondaryS3.dockerAwsS3().region())
            .uploadRetrySpec(Optional.of(Retry.backoff(3, java.time.Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .readTimeout(Optional.of(Duration.ofMillis(500)))
            .build(),
            SECONDARY_BUCKET_SUFFIX);
    }

    @AfterAll
    static void afterAll() {
        secondaryS3.stop();
    }

    @BeforeEach
    void beforeEach(GuiceJamesServer server) throws Exception {
        prepareBlobStore(server);

        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN.asString())
            .addUser(BOB.asString(), BOB_PASSWORD)
            .addUser(ANDRE.asString(), ANDRE_PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxPath.inbox(BOB));
        mailboxProbe.createMailbox(MailboxPath.inbox(ANDRE));

        UserCredential userCredential = new UserCredential(BOB, BOB_PASSWORD);
        PreemptiveBasicAuthScheme authScheme = new PreemptiveBasicAuthScheme();
        authScheme.setUserName(userCredential.username().asString());
        authScheme.setPassword(userCredential.password());
        requestSpecification = new RequestSpecBuilder()
            .setContentType(JSON)
            .setAccept(JSON)
            .setConfig(RestAssuredConfig.newConfig().encoderConfig(EncoderConfig.encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(server.getProbe(JmapGuiceProbe.class)
                .getJmapPort()
                .getValue())
            .setBasePath(JMAPUrls.JMAP)
            .setAuth(authScheme)
            .addHeader(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER)
            .build();
        EncryptHelper.uploadPublicKey(ACCOUNT_ID, requestSpecification);
    }

    void prepareBlobStore(GuiceJamesServer server) {
        if (secondaryS3.isPaused()) {
            secondaryS3.unpause();
        }
        BlobStoreProbe blobStoreProbe = server.getProbe(BlobStoreProbe.class);
        blobStoreProbe.getPrimaryBlobStoreDAO().deleteAllBuckets().block();
        blobStoreProbe.getSecondaryBlobStoreDAO().deleteAllBuckets().block();
    }

    @Test
    void sendEmailShouldResultingInSavingDataToBothObjectStorages(GuiceJamesServer server) {
        given()
            .body(LinagoraEmailSendMethodContract$.MODULE$.bobSendsAMailToAndre(server))
        .when()
            .post()
        .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract()
            .body()
            .asString();

        BlobStoreProbe blobStoreProbe = server.getProbe(BlobStoreProbe.class);

        calmlyAwait.atMost(TEN_SECONDS)
            .untilAsserted(() -> {
                BucketName bucketName = Flux.from(blobStoreProbe.getPrimaryBlobStoreDAO().listBuckets()).collectList().block().getFirst();
                BucketName secondaryBucketName = Flux.from(blobStoreProbe.getSecondaryBlobStoreDAO().listBuckets()).collectList().block().getFirst();
                List<BlobId> blobIds = Flux.from(blobStoreProbe.getPrimaryBlobStoreDAO().listBlobs(bucketName)).collectList().block();
                List<BlobId> blobIds2 = Flux.from(blobStoreProbe.getSecondaryBlobStoreDAO().listBlobs(secondaryBucketName)).collectList().block();
                assertThat(blobIds).hasSameSizeAs(blobIds2);
                assertThat(blobIds).hasSameElementsAs(blobIds2);
            });
    }

    @Test
    void sendEmailShouldResultingInEventuallySavingDataToBothObjectStoragesWhenSecondStorageIsDownForShortTime(GuiceJamesServer server) throws Exception {
        secondaryS3.pause();

        given()
            .body(LinagoraEmailSendMethodContract$.MODULE$.bobSendsAMailToAndre(server))
        .when()
            .post()
        .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract()
            .body()
            .asString();

        Thread.sleep(FIVE_SECONDS);
        secondaryS3.unpause();

        BlobStoreProbe blobStoreProbe = server.getProbe(BlobStoreProbe.class);
        BucketName bucketName = Flux.from(blobStoreProbe.getPrimaryBlobStoreDAO().listBuckets()).collectList().block().getFirst();
        List<BlobId> blobIds = Flux.from(blobStoreProbe.getPrimaryBlobStoreDAO().listBlobs(bucketName)).collectList().block();
        calmlyAwait.atMost(ONE_MINUTE)
            .untilAsserted(() -> {
                List<BlobId> blobIds2 = Flux.from(blobStoreProbe.getSecondaryBlobStoreDAO().listBlobs(BucketName.of(bucketName.asString() + SECONDARY_BUCKET_SUFFIX))).collectList().block();
                assertThat(blobIds2).hasSameSizeAs(blobIds);
                assertThat(blobIds2).hasSameElementsAs(blobIds);
            });
    }

    @Test
    void sendEmailShouldResultingInEventuallySavingDataToBothObjectStoragesWhenSecondStorageIsDownForLongTime(GuiceJamesServer server) {
        secondaryS3.pause();

        given()
            .body(LinagoraEmailSendMethodContract$.MODULE$.bobSendsAMailToAndre(server))
        .when()
            .post()
        .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract()
            .body()
            .asString();

        checkIfAllEventDeadLettersArePersisted(server);
        secondaryS3.unpause();

        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();

        // trigger reprocessing event dead letters
        String taskId = with()
            .queryParam("action", "reDeliver")
            .post(EventDeadLettersRoutes.BASE_PATH + "/groups/" + new FailedBlobOperationListener.FailedBlobOperationListenerGroup().asString())
            .then()
            .extract()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .queryParam("timeout", "1m")
            .get(taskId + "/await");

        BlobStoreProbe blobStoreProbe = server.getProbe(BlobStoreProbe.class);
        BucketName bucketName = Flux.from(blobStoreProbe.getPrimaryBlobStoreDAO().listBuckets()).collectList().block().getFirst();
        List<BlobId> expectedBlobIds = Flux.from(blobStoreProbe.getPrimaryBlobStoreDAO().listBlobs(bucketName)).collectList().block();
        calmlyAwait.atMost(TEN_SECONDS)
            .untilAsserted(() -> {
                List<BlobId> blobIds2 = Flux.from(blobStoreProbe.getSecondaryBlobStoreDAO().listBlobs(BucketName.of(bucketName.asString() + SECONDARY_BUCKET_SUFFIX))).collectList().block();
                assertThat(blobIds2).hasSameSizeAs(expectedBlobIds);
                assertThat(blobIds2).hasSameElementsAs(expectedBlobIds);
            });
    }

    private void checkIfAllEventDeadLettersArePersisted(GuiceJamesServer server) {
        BlobStoreProbe blobStoreProbe = server.getProbe(BlobStoreProbe.class);
        Group group = new FailedBlobOperationListener.FailedBlobOperationListenerGroup();
        calmlyAwait.atMost(ONE_MINUTE)
            .untilAsserted(() -> {
                assertThatCode(() -> {
                    BucketName bucketName = Flux.from(blobStoreProbe.getPrimaryBlobStoreDAO().listBuckets()).collectList().block().getFirst();
                    List<BlobId> expectedBlobIds = Flux.from(blobStoreProbe.getPrimaryBlobStoreDAO().listBlobs(bucketName)).collectList().block();
                    List<EventDeadLetters.InsertionId> insertionIds = blobStoreProbe.getEventDeadLetters().failedIds(group)
                        .collectList().block();
                    List<FailedBlobEvents.BlobAddition> events = insertionIds.stream()
                        .map(insertionId -> blobStoreProbe.getEventDeadLetters().failedEvent(group, insertionId).block())
                        .map(FailedBlobEvents.BlobAddition.class::cast)
                        .toList();
                    assertThat(bucketName).isEqualTo(events.getFirst().bucketName());
                    assertThat(events.stream().map(FailedBlobEvents.BlobAddition::blobId)).hasSameElementsAs(expectedBlobIds);
                }).doesNotThrowAnyException();
            });
    }
}
