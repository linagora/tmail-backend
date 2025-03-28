package com.linagora.tmail.conf;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.Properties;



public class AIBotConfigLoader {

    public static AIBotProperties loadConfiguration() throws FileNotFoundException ,IOException {
        Properties properties = new Properties();
        InputStream input = AIBotConfigLoader.class.getClassLoader().getResourceAsStream("ai.properties");
        if (input == null) {
                throw new FileNotFoundException("Le fichier de configuration 'ai.properties' est introuvable dans le classpath.");
            }
        properties.load(input);

        AIBotProperties config = new AIBotProperties();
        config.setApiKey(properties.getProperty("apiKey"));
        config.setBotAddress(properties.getProperty("botAddress"));
        config.setModel(properties.getProperty("model"));
        config.setBaseUrl(parseURL(properties.getProperty("baseURL")));

        return config;
    }

    private static Optional<URL> parseURL(String urlString) {
        try {
            return Optional.ofNullable(urlString).map(URI::create).map(uri -> {
                try {
                    return uri.toURL();
                } catch (MalformedURLException e) {
                    throw new RuntimeException("Invalid URL format: " + urlString, e);
                }
            });
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}