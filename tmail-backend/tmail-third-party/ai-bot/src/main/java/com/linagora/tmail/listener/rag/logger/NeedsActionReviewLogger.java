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
package com.linagora.tmail.listener.rag.logger;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class NeedsActionReviewLogger {
    private static final Logger LOG = LoggerFactory.getLogger("AI_REVIEW");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PREVIEW_MAX = 255;
    private final boolean enabled;

    public NeedsActionReviewLogger() {
        this.enabled = Boolean.parseBoolean(
            System.getProperty("tmail.ai.needsaction.relevance.review",
                System.getenv().getOrDefault("TMAIL_AI_NEEDSACTION_RELEVANCE_REVIEW", "false"))
        );
    }

    public void log(String sender, String user, String subject, boolean decision, String preview) {
        if (enabled) {
            String safeSubject = subject == null ? "" : subject;
            String safeSender = sender == null ? "" : sender;
            String safeUser = user == null ? "" : user;
            String safePreview = preview == null ? "" : truncate(preview);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sender", safeSender);
            payload.put("user", safeUser);
            payload.put("subject", safeSubject);
            payload.put("decision", decision);
            payload.put("preview", safePreview);

            try {
                String json = MAPPER.writeValueAsString(payload);
                LOG.info(json);
            } catch (Exception e) {
                LOG.warn("Failed to serialize AI_REVIEW payload", e);
            }
        }
    }

    private String truncate(String s) {
        return s.length() <= PREVIEW_MAX ? s : s.substring(0, PREVIEW_MAX);
    }
}