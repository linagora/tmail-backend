package com.linagora.tmail.blob.blobguice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.james.FakePropertiesProvider;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.server.blob.deduplication.StorageStrategy;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class BlobStoreConfigurationTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(BlobStoreConfiguration.class)
            .verify();
    }

    @Test
    void encryptionShouldRequirePassword() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", false);
        configuration.addProperty("encryption.aes.enable", true);
        // Hex.encode("salty".getBytes(StandardCharsets.UTF_8))
        configuration.addProperty("encryption.aes.salt", "73616c7479");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> BlobStoreConfiguration.parse(propertyProvider))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptionShouldRequireSalt() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", false);
        configuration.addProperty("encryption.aes.enable", true);
        configuration.addProperty("encryption.aes.password", "salty");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> BlobStoreConfiguration.parse(propertyProvider))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptionShouldBeDisabledByDefault() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", false);
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(BlobStoreConfiguration.parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                .disableCache()
                .passthrough()
                .noCryptoConfig()
                .disableSingleSave()
                .noSecondaryS3BlobStoreConfig());
    }

    @Test
    void encryptionShouldBeDisableable() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", false);
        configuration.addProperty("encryption.aes.enable", false);
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(BlobStoreConfiguration.parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                .disableCache()
                .passthrough()
                .noCryptoConfig()
                .disableSingleSave()
                .noSecondaryS3BlobStoreConfig());
    }

    @Test
    void encryptionCanBeActivated() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", false);
        configuration.addProperty("encryption.aes.enable", true);
        configuration.addProperty("encryption.aes.password", "myPass");
        // Hex.encode("salty".getBytes(StandardCharsets.UTF_8))
        configuration.addProperty("encryption.aes.salt", "73616c7479");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(BlobStoreConfiguration.parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                .disableCache()
                .passthrough()
                .cryptoConfig(CryptoConfig.builder()
                    .password("myPass".toCharArray())
                    .salt("73616c7479")
                    .build())
                .disableSingleSave()
                .noSecondaryS3BlobStoreConfig());
    }

    @Test
    void cacheEnabledShouldBeTrueWhenSpecified() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cache.enable", true);
        configuration.addProperty("deduplication.enable", "true");

        assertThat(BlobStoreConfiguration.from(configuration).cacheEnabled())
            .isTrue();
    }

    @Test
    void cacheEnabledShouldBeFalseWhenSpecified() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cache.enable", false);
        configuration.addProperty("deduplication.enable", "true");

        assertThat(BlobStoreConfiguration.from(configuration).cacheEnabled())
            .isFalse();
    }

    @Test
    void cacheEnabledShouldDefaultToFalse() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", "true");

        assertThat(BlobStoreConfiguration.from(configuration).cacheEnabled())
            .isFalse();
    }

    @Test
    void storageStrategyShouldBePassthroughWhenDeduplicationDisabled() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", "false");

        assertThat(BlobStoreConfiguration.from(configuration).storageStrategy())
            .isEqualTo(StorageStrategy.PASSTHROUGH);
    }

    @Test
    void storageStrategyShouldBeDeduplicationWhenDeduplicationEnabled() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", "true");

        assertThat(BlobStoreConfiguration.from(configuration).storageStrategy())
            .isEqualTo(StorageStrategy.DEDUPLICATION);
    }

    @Test
    void buildingConfigurationShouldThrowWhenDeduplicationPropertieIsOmitted() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration)).isInstanceOf(IllegalStateException.class)
            .hasMessage("""
                deduplication.enable property is missing please use one of the supported values in: true, false
                If you choose to enable deduplication, the mails with the same content will be stored only once.
                Warning: Once this feature is enabled, there is no turning back as turning it off will lead to the deletion of all
                the mails sharing the same content once one is deleted.
                Upgrade note: If you are upgrading from James 3.5 or older, the deduplication was enabled.""");
    }

    @Test
    void singleSaveEnabledShouldDefaultToFalse() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", "true");

        assertThat(BlobStoreConfiguration.from(configuration).singleSaveEnabled())
            .isFalse();
    }

    @Test
    void singleSaveEnabledShouldBeTrueWhenSpecified() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", "true");
        configuration.addProperty("single.save.enable", "true");

        assertThat(BlobStoreConfiguration.from(configuration).singleSaveEnabled())
            .isTrue();
    }

    @Test
    void singleSaveEnabledShouldBeFalseWhenSpecified() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", "true");
        configuration.addProperty("single.save.enable", "false");

        assertThat(BlobStoreConfiguration.from(configuration).singleSaveEnabled())
            .isFalse();
    }

    @Test
    void buildingConfigurationShouldThrowWhenSingleSavePropertyIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("deduplication.enable", "true");
        configuration.addProperty("single.save.enable", "invalid");

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration))
            .isInstanceOf(ConversionException.class);
    }
}
