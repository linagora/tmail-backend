package com.linagora.tmail.james.jmap;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.utils.PropertiesProvider;

import com.github.fge.lambdas.Throwing;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.james.jmap.jwt.JwtPrivateKeyConfiguration;
import com.linagora.tmail.james.jmap.jwt.JwtPrivateKeyProvider;

import scala.jdk.javaapi.OptionConverters;

public class ShortLivedTokenModule extends AbstractModule {

    @Provides
    @Singleton
    JwtPrivateKeyProvider jwtPrivateKeyProvider(PropertiesProvider propertiesProvider, FileSystem fileSystem) throws ConfigurationException, FileNotFoundException {
        Configuration configuration = propertiesProvider.getConfiguration("jmap");
        Optional<String> jwtPrivateKey = loadKey(fileSystem, Optional.ofNullable(configuration.getString("jwt.privatekeypem.url")));
        return new JwtPrivateKeyProvider(new JwtPrivateKeyConfiguration(OptionConverters.toScala(jwtPrivateKey)));
    }

    private Optional<String> loadKey(FileSystem fileSystem, Optional<String> jwtKeyPemUrl) {
        return jwtKeyPemUrl.map(Throwing.function(url -> FileUtils.readFileToString(fileSystem.getFile(url), StandardCharsets.US_ASCII)));
    }
}
