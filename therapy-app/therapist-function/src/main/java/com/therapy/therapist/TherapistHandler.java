package com.therapy.therapist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TherapistHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    // Connect to LocalStack DynamoDB; adjust endpoint if needed.
    private final AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()
       .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
               "http://host.docker.internal:4566", "ap-south-1"))
       .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("Received event: " + event);
        String path = (String) event.get("path");
        String httpMethod = (String) event.get("httpMethod");

        try {
            // Public endpoint to list all therapists
            if ("/therapists/public".equals(path) && "GET".equalsIgnoreCase(httpMethod)) {
                return formatResponse(200, getAllTherapists(context));
            }
            // PUT /therapists/update for updating specialization/availableSlots
            else if ("/therapists/update".equals(path) && "PUT".equalsIgnoreCase(httpMethod)) {
                return formatResponse(200, updateTherapistInfo(event, context));
            }
            // POST /therapists/update for modifying client list
            else if ("/therapists/update".equals(path) && "POST".equalsIgnoreCase(httpMethod)) {
                return formatResponse(200, modifyTherapistClientList(event, context));
            }
            return formatResponse(400, "{\"message\": \"Invalid endpoint or method\"}");
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return formatResponse(500, "{\"message\": \"Error: " + e.getMessage() + "\"}");
        }
    }

    // GET /therapists/public: Scan and return all therapist items.
    private String getAllTherapists(Context context) throws Exception {
        ScanRequest scanRequest = new ScanRequest().withTableName("therapist");
        ScanResult scanResult = dynamoDB.scan(scanRequest);
        return objectMapper.writeValueAsString(simplifyItems(scanResult.getItems()));
    }

    /**
     * PUT /therapists/update: Update therapist's specialization or availableSlots.
     * Expected JSON body:
     * {
     *   "therapistId": "some-therapist-id",
     *   "action": "ADD" or "REMOVE",
     *   "type": "specialization" or "availableSlots",
     *   "value": "value to add or remove"
     * }
     */
    // private String updateTherapistInfo(Map<String, Object> event, Context context) throws Exception {
    //     String bodyString = (String) event.get("body");
    //     Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);

    //     String therapistId = (String) body.get("therapistId");
    //     String action = (String) body.get("action");
    //     String type = (String) body.get("type");
    //     String value = (String) body.get("value");

    //     if (therapistId == null || action == null || type == null || value == null) {
    //         throw new Exception("Missing required fields in request body");
    //     }
    //     context.getLogger().log("Updating therapist " + therapistId + " - " + type + " " + action + " value: " + value);

    //     // Retrieve therapist record
    //     Map<String, AttributeValue> key = new HashMap<>();
    //     key.put("therapistId", new AttributeValue(therapistId));
    //     Map<String, AttributeValue> therapistItem = dynamoDB.getItem(new GetItemRequest().withTableName("therapist").withKey(key)).getItem();
    //     if (therapistItem == null || therapistItem.isEmpty()) {
    //         throw new Exception("Therapist not found");
    //     }

    //     // Determine the attribute to update based on 'type'
    //     String attributeName;
    //     if ("specialization".equalsIgnoreCase(type)) {
    //         attributeName = "specialization";
    //     } else if ("availableSlots".equalsIgnoreCase(type)) {
    //         attributeName = "availableSlots";
    //     } else {
    //         throw new Exception("Invalid type. Must be 'specialization' or 'availableSlots'.");
    //     }

    //     // Get existing list; if null, start with an empty list.
    //     List<AttributeValue> list = new ArrayList<>();
    //     if (therapistItem.get(attributeName) != null && therapistItem.get(attributeName).getL() != null) {
    //         list = therapistItem.get(attributeName).getL();
    //     }

    //     boolean modified = false;
    //     if ("ADD".equalsIgnoreCase(action)) {
    //         // Add value if not already present.
    //         boolean exists = list.stream().anyMatch(av -> value.equals(av.getS()));
    //         if (!exists) {
    //             list.add(new AttributeValue(value));
    //             modified = true;
    //         }
    //     } else if ("REMOVE".equalsIgnoreCase(action)) {
    //         int originalSize = list.size();
    //         list.removeIf(av -> value.equals(av.getS()));
    //         if (list.size() < originalSize) {
    //             modified = true;
    //         }
    //     } else {
    //         throw new Exception("Invalid action. Must be 'ADD' or 'REMOVE'.");
    //     }

    //     if (modified) {
    //         therapistItem.put(attributeName, new AttributeValue().withL(list));
    //         dynamoDB.putItem(new PutItemRequest("therapist", therapistItem));
    //     }
    //     return objectMapper.writeValueAsString(therapistItem);
    // }
    private String updateTherapistInfo(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
    
        String therapistId = (String) body.get("therapistId");
        String action = (String) body.get("action");
        String type = (String) body.get("type");
        String value = (String) body.get("value");
    
        if (therapistId == null || action == null || type == null || value == null) {
            throw new Exception("Missing required fields in request body");
        }
        context.getLogger().log("Updating therapist " + therapistId + " - " + type + " " + action + " value: " + value);
    
        // Retrieve therapist record
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("therapistId", new AttributeValue(therapistId));
        Map<String, AttributeValue> therapistItem = dynamoDB.getItem(
                new GetItemRequest().withTableName("therapist").withKey(key)).getItem();
        if (therapistItem == null || therapistItem.isEmpty()) {
            throw new Exception("Therapist not found");
        }
    
        // Determine which attribute to update based on 'type'
        String attributeName;
        if ("specialization".equalsIgnoreCase(type)) {
            attributeName = "specialization";
        } else if ("availableSlots".equalsIgnoreCase(type)) {
            attributeName = "availableSlots";
        } else {
            throw new Exception("Invalid type. Must be 'specialization' or 'availableSlots'.");
        }
    
        // Since the attribute is defined as a string (S), we treat it as a comma-separated list.
        String currentValue = "";
        if (therapistItem.get(attributeName) != null) {
            currentValue = therapistItem.get(attributeName).getS();
        }
        
        // Split current value by commas into a list
        List<String> list = new ArrayList<>();
        if (currentValue != null && !currentValue.trim().isEmpty()) {
            for (String s : currentValue.split(",")) {
                list.add(s.trim());
            }
        }
    
        boolean modified = false;
        if ("ADD".equalsIgnoreCase(action)) {
            if (!list.contains(value)) {
                list.add(value);
                modified = true;
            }
        } else if ("REMOVE".equalsIgnoreCase(action)) {
            if (list.contains(value)) {
                list.remove(value);
                modified = true;
            }
        } else {
            throw new Exception("Invalid action. Must be 'ADD' or 'REMOVE'.");
        }
    
        if (modified) {
            // Join the list back into a comma-separated string.
            String updatedValue = String.join(", ", list);
            therapistItem.put(attributeName, new AttributeValue(updatedValue));
            dynamoDB.putItem(new PutItemRequest("therapist", therapistItem));
        }
        return objectMapper.writeValueAsString(therapistItem);
    }
    
    
    // POST /therapists/update: Modify therapist-client mapping.
    // Expected body: { "therapistId": "...", "clientId": "...", "action": "ADD" or "REMOVE" }
    private String modifyTherapistClientList(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        String therapistId = (String) body.get("therapistId");
        String clientId = (String) body.get("clientId");
        String action = (String) body.get("action");
        if (therapistId == null || clientId == null || action == null) {
            throw new Exception("Missing required fields");
        }
        context.getLogger().log("Modifying therapist-client mapping: therapistId=" + therapistId + ", clientId=" + clientId + ", action=" + action);

        // Retrieve therapist record
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("therapistId", new AttributeValue(therapistId));
        Map<String, AttributeValue> therapistItem = dynamoDB.getItem(new GetItemRequest().withTableName("therapist").withKey(key)).getItem();
        if (therapistItem == null || therapistItem.isEmpty()) {
            throw new Exception("Therapist not found");
        }

        List<AttributeValue> clientsList = new ArrayList<>();
        if (therapistItem.get("clients") != null && therapistItem.get("clients").getL() != null) {
            clientsList = therapistItem.get("clients").getL();
        }
        boolean modified = false;
        if ("ADD".equalsIgnoreCase(action)) {
            boolean exists = clientsList.stream().anyMatch(av -> clientId.equals(av.getS()));
            if (!exists) {
                clientsList.add(new AttributeValue(clientId));
                modified = true;
            }
        } else if ("REMOVE".equalsIgnoreCase(action)) {
            int originalSize = clientsList.size();
            clientsList.removeIf(av -> clientId.equals(av.getS()));
            if (clientsList.size() < originalSize) {
                modified = true;
            }
        } else {
            throw new Exception("Invalid action. Must be 'ADD' or 'REMOVE'.");
        }
        if (modified) {
            therapistItem.put("clients", new AttributeValue().withL(clientsList));
            dynamoDB.putItem(new PutItemRequest("therapist", therapistItem));
        }
        return objectMapper.writeValueAsString(therapistItem);
    }

    // Helper method: converts a list of DynamoDB items to a simplified structure (only string values)
    private List<Map<String, String>> simplifyItems(List<Map<String, AttributeValue>> items) {
        List<Map<String, String>> simpleList = new ArrayList<>();
        for (Map<String, AttributeValue> item : items) {
            Map<String, String> simpleItem = new HashMap<>();
            for (String key : item.keySet()) {
                AttributeValue av = item.get(key);
                simpleItem.put(key, av.getS());
            }
            simpleList.add(simpleItem);
        }
        return simpleList;
    }

    private Map<String, Object> formatResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        response.put("statusCode", statusCode);
        response.put("headers", headers);
        response.put("body", body);
        return response;
    }
}
