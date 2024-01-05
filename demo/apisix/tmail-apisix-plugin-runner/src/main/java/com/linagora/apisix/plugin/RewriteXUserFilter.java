package com.linagora.apisix.plugin;

import static com.linagora.apisix.plugin.TokenRevokedFilter.makeUnAuthorizedRequest;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import org.apache.apisix.plugin.runner.HttpRequest;
import org.apache.apisix.plugin.runner.HttpResponse;
import org.apache.apisix.plugin.runner.filter.PluginFilter;
import org.apache.apisix.plugin.runner.filter.PluginFilterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

@Component
public class RewriteXUserFilter implements PluginFilter {
    public static final String TMAIL_X_USER_HEADER_NAME = "X-User";
    private static final String PRE_FILTER = "pre";
    private static final String POST_FILTER = "post";
    private final Logger logger = LoggerFactory.getLogger("RewriteXUserPlugin");
    private final ObjectMapper json;
    private final String userInfoField;

    @Autowired
    public RewriteXUserFilter(@Value("${app.rewrite_xuser.userinfo_field}") String xUserUserInfoField) {
        this.json = new ObjectMapper();
        this.userInfoField = xUserUserInfoField;
        Preconditions.checkArgument(StringUtils.hasText(userInfoField));
        logger.debug("RewriteXUserPlugin init with userinfo field: {}", userInfoField);
    }

    @Override
    public String name() {
        return "RewriteXUserFilter";
    }

    @Override
    public void filter(HttpRequest request, HttpResponse response, PluginFilterChain chain) {
        final String mode = request.getConfig(this);
        logger.debug("Received a new request. Filter mode: {}", mode);
        if (mode.equalsIgnoreCase(PRE_FILTER)) {
            xUserHeaderConditionCheck(request, response);
        } else if (mode.equalsIgnoreCase(POST_FILTER)) {
            Optional.ofNullable(request.getHeader("X-Userinfo"))
                .or(() -> Optional.ofNullable(request.getHeader("x-userinfo")))
                .flatMap(this::extractUserFromUserInfo)
                .ifPresent(user -> {
                    request.setHeader(TMAIL_X_USER_HEADER_NAME, user);
                    logger.debug("Rewrite X-User header. {}", user);
                });
        }
        chain.filter(request, response);
    }

    private void xUserHeaderConditionCheck(HttpRequest request, HttpResponse response) {
        Optional.ofNullable(request.getHeader(TMAIL_X_USER_HEADER_NAME))
            .or(() -> Optional.ofNullable(request.getHeader(TMAIL_X_USER_HEADER_NAME.toLowerCase(Locale.US))))
            .ifPresent(deniedHeader -> {
                makeUnAuthorizedRequest(request, response);
                logger.debug("The client request has X-User header has been denied. {}", deniedHeader);
            });
    }

    public Optional<String> extractUserFromUserInfo(String userInfo) {
        try {
            JsonNode userInfoAsJsonNode = json.readTree(Base64.getDecoder().decode(userInfo));

            if (userInfoAsJsonNode.hasNonNull(userInfoField)) {
                JsonNode jsonNode = userInfoAsJsonNode.get(userInfoField);
                if (jsonNode.isTextual()) {
                    return Optional.of(jsonNode.asText());
                }
                logger.warn("Wrong userinfo field userInfo {}", jsonNode.getClass());
            } else {
                logger.warn("JSON of userInfo does not contain property '{}'", userInfoField);
            }
            return Optional.empty();
        } catch (IOException e) {
            logger.warn("Can not extract user from userInfo header", e);
            return Optional.empty();
        }
    }
}