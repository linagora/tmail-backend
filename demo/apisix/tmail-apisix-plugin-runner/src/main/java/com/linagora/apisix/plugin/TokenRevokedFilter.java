package com.linagora.apisix.plugin;

import java.util.Optional;

import org.apache.apisix.plugin.runner.HttpRequest;
import org.apache.apisix.plugin.runner.HttpResponse;
import org.apache.apisix.plugin.runner.filter.PluginFilter;
import org.apache.apisix.plugin.runner.filter.PluginFilterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TokenRevokedFilter implements PluginFilter {
    private final Logger logger = LoggerFactory.getLogger("RevokedTokenPlugin");
    private final IRevokedTokenRepository tokenRepository;

    @Autowired
    public TokenRevokedFilter(IRevokedTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Override
    public String name() {
        return "TokenRevokedFilter";
    }

    @Override
    public void filter(HttpRequest request, HttpResponse response, PluginFilterChain chain) {
        logger.debug("Received a new request");
        sidFilter(request, response);
        chain.filter(request, response);
    }

    private void sidFilter(HttpRequest request, HttpResponse response) {
        Optional.ofNullable(request.getHeader("Authorization"))
            .or(() -> Optional.ofNullable(request.getHeader("authorization")))
            .map(String::trim)
            .map(bearerToken -> bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken)
            .flatMap(ChannelLogoutController::extractSidFromLogoutToken)
            .ifPresent(sid -> {
                boolean existSid = tokenRepository.exist(sid);
                if (existSid) {
                    logger.info("Token has been revoked, Sid: " + sid);
                    makeUnAuthorizedRequest(request, response);
                } else {
                    logger.debug("Token valid, Sid: " + sid);
                }
            });
    }

    public static void makeUnAuthorizedRequest(HttpRequest request, HttpResponse response) {
        response.setStatusCode(401);
        response.setHeader("Content-Type","text/html; charset=utf-8");
        response.setBody("<html>\n" +
            "<head><title>401 Authorization Required</title></head>\n" +
            "<body>\n" +
            "<center><h1>401 Authorization Required</h1></center>\n" +
            "<hr><center>openresty</center>\n" +
            "<p><em>Powered by <a href=\"https://apisix.apache.org/\">APISIX</a>.</em></p></body>\n" +
            "</html>\n");
    }


}