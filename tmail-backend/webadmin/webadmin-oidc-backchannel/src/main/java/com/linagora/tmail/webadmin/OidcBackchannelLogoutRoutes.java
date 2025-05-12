/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.webadmin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;
import com.linagora.tmail.james.jmap.oidc.OidcTokenCache;
import com.linagora.tmail.james.jmap.oidc.Sid;

import spark.Route;
import spark.Service;

public class OidcBackchannelLogoutRoutes implements Routes {
    public static final String BASE_PATH = "/add-revoked-token";

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcBackchannelLogoutRoutes.class);
    private static final String APPLICATION_FORM_URLENCODED_VALUE = "application/x-www-form-urlencoded";
    private static final String TOKEN_PARAM = "logout_token";
    private static final String SID_PROPERTY = "sid";

    private final OidcTokenCache oidcTokenCache;
    private final ObjectMapper objectMapper;
    private final JsonTransformer jsonTransformer;

    @Inject
    public OidcBackchannelLogoutRoutes(OidcTokenCache oidcTokenCache, JsonTransformer jsonTransformer) {
        this.oidcTokenCache = oidcTokenCache;
        this.jsonTransformer = jsonTransformer;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(BASE_PATH, addRevokedToken(), jsonTransformer);
    }

    public Route addRevokedToken() {
        return (request, response) -> {
            if (!StringUtils.startsWith(request.contentType(), APPLICATION_FORM_URLENCODED_VALUE)) {
                response.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE_415);
                return "Unsupported Content-Type";
            }

            String token = request.queryParams(TOKEN_PARAM);
            Preconditions.checkArgument(StringUtils.isNotEmpty(token), "Missing logout token");

            try {
                extractSidFromLogoutToken(token)
                    .ifPresentOrElse(sid -> {
                        LOGGER.debug("Add new revoked token has sid: " + sid);
                        oidcTokenCache.invalidate(sid).block();
                    }, () -> LOGGER.warn("Cannot extract sid from logout token: {}", token));
                response.status(HttpStatus.OK_200);
                return Constants.EMPTY_BODY;
            } catch (Exception e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                    .type(ErrorResponder.ErrorType.SERVER_ERROR)
                    .message(String.format("Error while adding revoked token '%s'", token))
                    .cause(e)
                    .haltError();
            }
        };
    }

    private Optional<Sid> extractSidFromLogoutToken(String token) {
        try {
            List<String> parts = Splitter.on('.')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(token);
            if (parts.size() < 2) {
                return Optional.empty();
            }

            String payloadJson = new String(BaseEncoding.base64Url().decode(parts.get(1)), StandardCharsets.UTF_8);
            Map<String, Object> payloadMap = objectMapper.readValue(payloadJson, new TypeReference<>() {
            });

            return Optional.ofNullable((String) payloadMap.getOrDefault(SID_PROPERTY, null))
                .map(Sid::new);
        } catch (Exception exception) {
            LOGGER.warn("Unable to extract Sid from logout token: '{}'", token, exception);
            return Optional.empty();
        }
    }
}
