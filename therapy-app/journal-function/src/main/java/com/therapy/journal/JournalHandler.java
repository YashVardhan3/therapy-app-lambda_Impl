package com.therapy.journal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JournalHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    // Connect to LocalStack DynamoDB (adjust endpoint as needed)
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
            if ("/journals".equals(path)) {
                if ("POST".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(201, createOrUpdateJournal(event, context));
                } else if ("GET".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, getJournals(event, context));
                } else if ("DELETE".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(204, deleteJournal(event, context));
                }
            } else if ("/journals/manage-therapist".equals(path) && "POST".equalsIgnoreCase(httpMethod)) {
                return formatResponse(200, modifyJournalTherapist(event, context));
            } else if ("/journals/emotion".equals(path) && "POST".equalsIgnoreCase(httpMethod)) {
                return formatResponse(200, addEmotionToJournal(event, context));
            } else {
                return formatResponse(400, "{\"message\": \"Invalid endpoint or method\"}");
            }
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return formatResponse(500, "{\"message\": \"Error: " + e.getMessage() + "\"}");
        }
        return formatResponse(400, "{\"message\": \"Invalid endpoint or method\"}");
    }

    /**
     * POST /journals
     * Create or update a journal. If the request body contains a "journalUuid", update that journal;
     * otherwise, create a new one with an auto-generated journalUuid.
     * Expected JournalRequest: { "clientId": "...", "title": "...", "content": "..." }
     */
    private String createOrUpdateJournal(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);

        String journalUuid = body.containsKey("journalUuid") ? (String) body.get("journalUuid") : null;
        String clientId = (String) body.get("clientId");
        String title = (String) body.get("title");
        String content = (String) body.get("content");

        if (clientId == null || title == null || content == null) {
            throw new Exception("Missing required fields in JournalRequest");
        }

        Map<String, AttributeValue> item = new HashMap<>();
        // If updating, use provided journalUuid; otherwise generate a new one.
        if (journalUuid == null || journalUuid.trim().isEmpty()) {
            journalUuid = UUID.randomUUID().toString();
        }
        item.put("journalId", new AttributeValue(journalUuid));
        item.put("clientId", new AttributeValue(clientId));
        item.put("title", new AttributeValue(title));
        item.put("content", new AttributeValue(content));
        // Optionally add a timestamp

        dynamoDB.putItem(new PutItemRequest("journal", item));
        return objectMapper.writeValueAsString(item);
    }

    /**
     * GET /journals
     * Supports optional filters: journalUuid, clientId, keyword, therapistId.
     */
    private String getJournals(Map<String, Object> event, Context context) throws Exception {
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        if (queryParams != null && queryParams.get("journalUuid") != null) {
            String journalUuid = queryParams.get("journalUuid");
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("journalId", new AttributeValue(journalUuid));
            Map<String, AttributeValue> result = dynamoDB.getItem(
                    new GetItemRequest().withTableName("journal").withKey(key)).getItem();
            if (result == null || result.isEmpty()) {
                return "[]";
            } else {
                List<Map<String, AttributeValue>> list = new ArrayList<>();
                list.add(result);
                return objectMapper.writeValueAsString(simplifyItems(list));
            }
        } else if (queryParams != null && queryParams.get("clientId") != null) {
            String clientId = queryParams.get("clientId");
            Map<String, AttributeValue> exprValues = new HashMap<>();
            exprValues.put(":cid", new AttributeValue(clientId));
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("journal")
                    .withFilterExpression("clientId = :cid")
                    .withExpressionAttributeValues(exprValues);
            ScanResult scanResult = dynamoDB.scan(scanRequest);
            return objectMapper.writeValueAsString(simplifyItems(scanResult.getItems()));
        } else if (queryParams != null && queryParams.get("keyword") != null) {
            String keyword = queryParams.get("keyword");
            Map<String, AttributeValue> exprValues = new HashMap<>();
            exprValues.put(":kw", new AttributeValue(keyword));
            // For simplicity, filter on content only.
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("journal")
                    .withFilterExpression("contains(content, :kw)")
                    .withExpressionAttributeValues(exprValues);
            ScanResult scanResult = dynamoDB.scan(scanRequest);
            return objectMapper.writeValueAsString(simplifyItems(scanResult.getItems()));
        } else if (queryParams != null && queryParams.get("therapistId") != null) {
            // Optionally support filtering by therapistId if stored in the journal item.
            String therapistId = queryParams.get("therapistId");
            Map<String, AttributeValue> exprValues = new HashMap<>();
            exprValues.put(":tid", new AttributeValue(therapistId));
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("journal")
                    .withFilterExpression("contains(therapist, :tid)")
                    .withExpressionAttributeValues(exprValues);
            ScanResult scanResult = dynamoDB.scan(scanRequest);
            return objectMapper.writeValueAsString(simplifyItems(scanResult.getItems()));
        } else {
            // If no filter provided, throw an error or return empty list.
            throw new Exception("Missing query fields in Request");
        }
    }

    /**
     * DELETE /journals?journalUuid=...
     */
    private String deleteJournal(Map<String, Object> event, Context context) throws Exception {
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        if (queryParams == null || queryParams.get("journalUuid") == null) {
            throw new Exception("Missing query parameter: journalUuid");
        }
        String journalUuid = queryParams.get("journalUuid");
        context.getLogger().log("Deleting journal with journalId: " + journalUuid);

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("journalId", new AttributeValue(journalUuid));
        dynamoDB.deleteItem(new DeleteItemRequest().withTableName("journal").withKey(key));
        return "{\"message\": \"Journal deleted successfully\"}";
    }

    /**
     * POST /journals/manage-therapist
     * Body: { "journalId": "...", "therapistId": "...", "action": "ADD" or "REMOVE" }
     */
    private String modifyJournalTherapist(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        String journalId = (String) body.get("journalId");
        String therapistId = (String) body.get("therapistId");
        String action = (String) body.get("action");
        if (journalId == null || therapistId == null || action == null) {
            throw new Exception("Missing required fields");
        }
        context.getLogger().log("JournalId: " + journalId + ", Action: " + action + ", TherapistId: " + therapistId);
        
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("journalId", new AttributeValue(journalId));
        Map<String, AttributeValue> journalItem = dynamoDB.getItem(
                new GetItemRequest().withTableName("journal").withKey(key)).getItem();
        if (journalItem == null || journalItem.isEmpty()) {
            throw new Exception("Journal not found");
        }
        
        List<AttributeValue> therapistList = new ArrayList<>();
        if (journalItem.get("therapists") != null && journalItem.get("therapists").getL() != null) {
            therapistList = journalItem.get("therapists").getL();
        }
        boolean modified = false;
        if ("ADD".equalsIgnoreCase(action)) {
            boolean exists = therapistList.stream().anyMatch(av -> therapistId.equals(av.getS()));
            if (!exists) {
                therapistList.add(new AttributeValue(therapistId));
                modified = true;
            }
        } else if ("REMOVE".equalsIgnoreCase(action)) {
            int sizeBefore = therapistList.size();
            therapistList.removeIf(av -> therapistId.equals(av.getS()));
            if (therapistList.size() < sizeBefore) {
                modified = true;
            }
        } else {
            throw new Exception("Invalid action");
        }
        if (modified) {
            journalItem.put("therapists", new AttributeValue().withL(therapistList));
            dynamoDB.putItem(new PutItemRequest("journal", journalItem));
            return objectMapper.writeValueAsString(journalItem);
        } else {
            return "{\"message\": \"No changes made\"}";
        }
    }

    /**
     * POST /journals/emotion
     * Body: { "journalId": 123, "emotion": { "name": "...", "intensity": 5, "timestamp": "..." } }
     * Adds an emotion to the journal item's "emotions" list.
     */
    private String addEmotionToJournal(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        Object journalIdObj = body.get("journalId");
        if (journalIdObj == null) {
            throw new Exception("Missing journalId in request body");
        }
        String journalId = journalIdObj.toString();
        Map<String, Object> emotion = (Map<String, Object>) body.get("emotion");
        if (emotion == null) {
            throw new Exception("Missing emotion object");
        }
        context.getLogger().log("Adding emotion to journal " + journalId);

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("journalId", new AttributeValue(journalId));
        Map<String, AttributeValue> journalItem = dynamoDB.getItem(
                new GetItemRequest().withTableName("journal").withKey(key)).getItem();
        if (journalItem == null || journalItem.isEmpty()) {
            throw new Exception("Journal not found");
        }
        
        List<AttributeValue> emotionsList = new ArrayList<>();
        if (journalItem.get("emotions") != null && journalItem.get("emotions").getL() != null) {
            emotionsList = journalItem.get("emotions").getL();
        }
        Map<String, AttributeValue> emotionAV = new HashMap<>();
        if (emotion.get("name") != null) {
            emotionAV.put("name", new AttributeValue(emotion.get("name").toString()));
        }
        if (emotion.get("intensity") != null) {
            emotionAV.put("intensity", new AttributeValue(emotion.get("intensity").toString()));
        }
        if (emotion.get("timestamp") != null) {
            emotionAV.put("timestamp", new AttributeValue(emotion.get("timestamp").toString()));
        }
        emotionsList.add(new AttributeValue().withM(emotionAV));
        journalItem.put("emotions", new AttributeValue().withL(emotionsList));
        
        dynamoDB.putItem(new PutItemRequest("journal", journalItem));
        return objectMapper.writeValueAsString(journalItem);
    }
    
    // Helper method to simplify a list of DynamoDB items to simple maps (only string values)
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
