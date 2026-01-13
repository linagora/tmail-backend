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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.vault.postgres;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.vault.dto.DeletedMessageWithStorageInformationConverter;
import org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTO;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

class TmailMetadataSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TmailMetadataSerializer.class);

    private final ObjectMapper objectMapper;
    private final DeletedMessageWithStorageInformationConverter dtoConverter;

    @Inject
    TmailMetadataSerializer(DeletedMessageWithStorageInformationConverter dtoConverter) {
        this.dtoConverter = dtoConverter;
        this.objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
    }

    Optional<DeletedMessageWithStorageInformation> deserialize(String payload) {
        return deserializeDto(payload)
            .flatMap(this::toDomainObject);
    }

    private Optional<DeletedMessageWithStorageInformationDTO> deserializeDto(String payload) {
        try {
            return Optional.of(objectMapper.readValue(payload, DeletedMessageWithStorageInformationDTO.class));
        } catch (Exception e) {
            LOGGER.error("Error deserializing JSON metadata", e);
            return Optional.empty();
        }
    }

    private Optional<DeletedMessageWithStorageInformation> toDomainObject(DeletedMessageWithStorageInformationDTO dto) {
        try {
            return Optional.of(dtoConverter.toDomainObject(dto));
        } catch (Exception e) {
            LOGGER.error("Error deserializing DTO", e);
            return Optional.empty();
        }
    }

    String serialize(DeletedMessageWithStorageInformation message) {
        DeletedMessageWithStorageInformationDTO dto = DeletedMessageWithStorageInformationDTO.toDTO(message);

        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
