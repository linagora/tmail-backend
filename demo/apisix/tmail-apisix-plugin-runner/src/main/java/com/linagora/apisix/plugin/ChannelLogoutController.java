package com.linagora.apisix.plugin;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.JWT;

@RestController
public class ChannelLogoutController {
    private final Logger logger = LoggerFactory.getLogger("RevokedTokenPlugin");

    private final IRevokedTokenRepository tokenRepository;
    public static final String TOKEN_PARAM = "logout_token";

    @Autowired
    public ChannelLogoutController(IRevokedTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @PostMapping(path = "/add-revoked-token", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<?> addRevokedToken(@RequestParam MultiValueMap<String, String> paramMap) {
        Optional.ofNullable(paramMap.getFirst(TOKEN_PARAM))
            .flatMap(ChannelLogoutController::extractSidFromLogoutToken)
            .ifPresentOrElse(sid -> {
                logger.debug("Add new revoked token has sid: " + sid);
                tokenRepository.add(sid).block();
            }, () -> logger.warn("`{}` is missing or invalid in request", TOKEN_PARAM));

        return ResponseEntity.ok().build();
    }

    public static Optional<String> extractSidFromLogoutToken(String token) {
        try {
            return Optional.of(JWT.decode(token).getClaim("sid").asString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
