package com.linagora.tmail.webadmin.model;

public class RateLimitingPlanIdResponse {
    private final String planId;

    public RateLimitingPlanIdResponse(String planId) {
        this.planId = planId;
    }

    public String getPlanId() {
        return planId;
    }
}
