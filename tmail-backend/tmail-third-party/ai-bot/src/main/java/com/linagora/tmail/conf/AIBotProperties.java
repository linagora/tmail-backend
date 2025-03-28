package com.linagora.tmail.conf;


import java.net.URL;
import java.util.Optional;

public class AIBotProperties {
    private String apiKey;
    private String botAddress;
    private String model;
    private Optional<URL> baseUrl;

    // Getters and setters
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBotAddress() {
        return botAddress;
    }

    public void setBotAddress(String botAddress) {
        this.botAddress = botAddress;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Optional<URL> getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(Optional<URL> baseUrl) {
        this.baseUrl = baseUrl;
    }
}