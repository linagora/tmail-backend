package com.linagora.tmail.mailet;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public enum Llm {
    OPEN_AI("openai");

    private String name;

    Llm(String name) {
        this.name = name;
    }

    public static Optional<Llm> fromName(String name) {
        return Arrays.stream(values())
                .filter(llm -> Objects.equals(llm.name, name))
                .findAny();
    }

    public static List<String> getSupportedLlmNames() {
        return Arrays.stream(values())
                .map(Llm::name)
                .toList();
    }
}
