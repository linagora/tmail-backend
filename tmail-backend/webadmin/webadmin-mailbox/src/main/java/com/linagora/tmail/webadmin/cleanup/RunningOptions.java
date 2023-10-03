package com.linagora.tmail.webadmin.cleanup;

import com.google.common.base.Preconditions;

public class RunningOptions {
    public static final int DEFAULT_USERS_PER_SECOND = 1;
    public static final RunningOptions DEFAULT = of(DEFAULT_USERS_PER_SECOND);

    public static RunningOptions of(int usersPerSecond) {
        return new RunningOptions(usersPerSecond);
    }

    private final int usersPerSecond;

    private RunningOptions(int usersPerSecond) {
        Preconditions.checkArgument(usersPerSecond > 0, "'usersPerSecond' needs to be strictly positive");

        this.usersPerSecond = usersPerSecond;
    }

    public int getUsersPerSecond() {
        return usersPerSecond;
    }
}
