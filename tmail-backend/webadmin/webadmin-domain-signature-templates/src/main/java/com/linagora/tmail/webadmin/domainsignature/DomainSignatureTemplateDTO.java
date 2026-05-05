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
 *******************************************************************/

package com.linagora.tmail.webadmin.domainsignature;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.james.jmap.event.DomainSignatureTemplate;
import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;

public record DomainSignatureTemplateDTO(
    @JsonProperty("signatures") List<SignatureEntryDTO> signatures) {

    public record SignatureEntryDTO(
        @JsonProperty("language") String language,
        @JsonProperty("textSignature") String textSignature,
        @JsonProperty("htmlSignature") String htmlSignature) {
    }

    public static DomainSignatureTemplateDTO from(DomainSignatureTemplate template) {
        List<SignatureEntryDTO> entries = template.templates().entrySet().stream()
            .map(entry -> new SignatureEntryDTO(
                entry.getKey().toLanguageTag(),
                entry.getValue().textSignature(),
                entry.getValue().htmlSignature()))
            .collect(Collectors.toList());
        return new DomainSignatureTemplateDTO(entries);
    }

    public DomainSignatureTemplate toDomain() {
        if (signatures == null) {
            throw new IllegalArgumentException("'signatures' field is mandatory");
        }
        Map<java.util.Locale, SignatureText> templates = signatures.stream()
            .collect(Collectors.toMap(
                entry -> java.util.Locale.forLanguageTag(entry.language()),
                entry -> new SignatureText(entry.textSignature(), entry.htmlSignature()),
                (a, b) -> {
                    throw new IllegalArgumentException("Duplicate language tag in signatures");
                }));
        return new DomainSignatureTemplate(templates);
    }
}
