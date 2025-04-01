package com.linagora.tmail.mailet;

import static org.junit.jupiter.api.Assertions.*;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.linagora.tmail.conf.AIBotConfigModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AIBotConfigIntegrationTest {

    private Injector injector;

    @BeforeEach
    public void setup() {

        injector = Guice.createInjector(new AIBotConfigModule());
    }

    @Test
    public void shouldLoadAIBotConfig() throws FileNotFoundException {

        AIBotConfig aiBotConfig = injector.getInstance(AIBotConfig.class);
        assertNotNull(aiBotConfig);
    }

    @Test
    public void shouldFindAIPropertiesFile() {

        Path configPath = Paths.get("conf/ai.properties");
        assertTrue(Files.exists(configPath), "Le fichier ai.properties n'existe pas !");
    }
}

