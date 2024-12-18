package com.linagora.tmail.carddav;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CardDavCreationSerializer {

    public final ObjectMapper objectMapper;

    public CardDavCreationSerializer() {
        this.objectMapper = new ObjectMapper();
    }

    public String serializeAsString(CardDavCreationObjectRequest request) throws JsonProcessingException {
        return objectMapper.writeValueAsString(toArrayNode(request));
    }

    public byte[] serializeAsBytes(CardDavCreationObjectRequest request) throws JsonProcessingException {
        return objectMapper.writeValueAsBytes(toArrayNode(request));
    }

    public ArrayNode toArrayNode(CardDavCreationObjectRequest request) {
        ArrayNode rootArray = objectMapper.createArrayNode();
        rootArray.add("vcard");

        ArrayNode vcardData = objectMapper.createArrayNode();

        vcardData.add(createTextField("version", request.version()));
        vcardData.add(createTextField("uid", request.uid()));

        request.fullName().ifPresent(fullName ->
            vcardData.add(createTextField("fn", fullName))
        );

        request.nameList().ifPresent(nameList ->
            vcardData.add(createTextField("n", nameList))
        );

        ObjectNode emailNode = objectMapper.createObjectNode();
        ArrayNode typeArrayNode = objectMapper.createArrayNode();

        List<String> emailTypes = request.email().type().stream()
            .map(CardDavCreationObjectRequest.EmailType::getValue)
            .toList();

        emailTypes.forEach(typeArrayNode::add);
        emailNode.set("type", typeArrayNode);
        ArrayNode emailField = objectMapper.createArrayNode();
        emailField.add("email");
        emailField.add(emailNode);
        emailField.add("text");
        emailField.add("mailto:" + request.email().value().asString());

        vcardData.add(emailField);
        rootArray.add(vcardData);
        rootArray.add(objectMapper.createArrayNode());
        return rootArray;
    }

    private ArrayNode createTextField(String fieldName, String value) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        arrayNode.add(fieldName);
        arrayNode.add(objectMapper.createObjectNode());
        arrayNode.add("text");
        arrayNode.add(value);
        return arrayNode;
    }

    private ArrayNode createTextField(String fieldName, List<String> values) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        arrayNode.add(fieldName);
        arrayNode.add(objectMapper.createObjectNode());
        arrayNode.add("text");
        arrayNode.add(objectMapper.valueToTree(values));
        return arrayNode;
    }
}
