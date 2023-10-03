package com.linagora.tmail.webadmin.cleanup;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RunningOptionsDTO(@JsonProperty("usersPerSecond") Optional<Integer> usersPerSecond) {

    public static RunningOptionsDTO asDTO(RunningOptions runningOptions) {
        return new RunningOptionsDTO(Optional.of(runningOptions.getUsersPerSecond()));
    }

    public RunningOptions asDomainObject() {
        return RunningOptions.of(usersPerSecond.orElse(RunningOptions.DEFAULT_USERS_PER_SECOND));
    }

}
