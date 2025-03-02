package com.therapy.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

public class SessionHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    // Connect to LocalStack DynamoDB (adjust as needed)
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
            if ("/sessions".equals(path)) {
                if ("POST".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, createSession(event, context));
                } else if ("GET".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, getSessions(event, context));
                } else if ("PUT".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, modifySession(event, context));
                }
            } else if ("/sessions/public".equals(path) && "GET".equalsIgnoreCase(httpMethod)) {
                return formatResponse(200, findOpenSessions(context));
            } else if ("/sessions/notes".equals(path) && "POST".equalsIgnoreCase(httpMethod)) {
                return formatResponse(200, getOrSearchNotes(event, context));
            } else {
                return formatResponse(400, "{\"message\": \"Invalid endpoint or method\"}");
            }
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return formatResponse(500, "{\"message\": \"Error: " + e.getMessage() + "\"}");
        }
        return formatResponse(500, "{\"message\": \"Invalid endpoint or method\"}");
    }

    /**
     * POST /sessions
     * SessionRequest example:
     * { "clientId": "...", "therapistId": "...", "isOpen": true, "sessionDate": "2025-02-28T14:00:00Z",
     *   "privatenotes": "some notes", "sharedNotes": "notes for client", "status": "SCHEDULED" }
     */
    private String createSession(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);

        // Generate a sessionId if not provided.
        String sessionId = body.containsKey("sessionId") && body.get("sessionId") != null && !body.get("sessionId").toString().trim().isEmpty()
                ? body.get("sessionId").toString()
                : UUID.randomUUID().toString();

        // Required: therapistId and sessionDate must be provided.
        if (body.get("therapistId") == null || body.get("sessionDate") == null) {
            throw new Exception("Missing required fields in SessionRequest");
        }

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("sessionId", new AttributeValue(sessionId));
        // Store clientId if provided; if not, it may be set later via modifySession.
        if (body.get("clientId") != null) {
            item.put("clientId", new AttributeValue(body.get("clientId").toString()));
        }
        item.put("therapistId", new AttributeValue(body.get("therapistId").toString()));
        if (body.get("isOpen")==null) {
            if (body.get("clientId")==null) {
                item.put("isOpen", new AttributeValue().withBOOL(true));
            }
            else {
                item.put("isOpen", new AttributeValue().withBOOL(false));
            }
        }
        else {
            if (body.get("isOpen").toString().equalsIgnoreCase("true")) {
                item.put("isOpen", new AttributeValue().withBOOL(true));
            }
            else if (body.get("clientId")!=null) {
                item.put("isOpen", new AttributeValue().withBOOL(false));
            }
            else {
                item.put("isOpen", new AttributeValue().withBOOL(true));
            }
        }
        item.put("sessionDate", new AttributeValue(body.get("sessionDate").toString()));
        item.put("privatenotes", new AttributeValue(body.get("privatenotes") != null ? body.get("privatenotes").toString() : ""));
        item.put("sharedNotes", new AttributeValue(body.get("sharedNotes") != null ? body.get("sharedNotes").toString() : ""));
        if (body.get("status") != null) {
            item.put("status", new AttributeValue(body.get("status").toString()));
        }

        dynamoDB.putItem(new PutItemRequest("session", item));
        return objectMapper.writeValueAsString(item);
    }

    /**
     * GET /sessions
     * Optional query parameters: userId, isOpen.
     * Filters sessions where either clientId or therapistId equals the given userId.
     */
    private String getSessions(Map<String, Object> event, Context context) throws Exception {
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        String filterExpression = "";
        Map<String, AttributeValue> exprValues = new HashMap<>();

        if (queryParams != null) {
            if (queryParams.get("userId") != null) {
                String userId = queryParams.get("userId");
                // Assumes session stores user references in clientId and therapistId.
                filterExpression = "(clientId = :uid or therapistId = :uid)";
                exprValues.put(":uid", new AttributeValue(userId));
            }
            if (queryParams.get("isOpen") != null) {
                String isOpen = queryParams.get("isOpen");
                if (!filterExpression.isEmpty()) {
                    filterExpression += " and ";
                }
                filterExpression += "isOpen = :io";
                exprValues.put(":io", new AttributeValue().withBOOL(Boolean.parseBoolean(isOpen)));
            }
        }

        ScanRequest scanRequest = new ScanRequest().withTableName("session");
        if (!filterExpression.isEmpty()) {
            scanRequest.withFilterExpression(filterExpression).withExpressionAttributeValues(exprValues);
        }
        ScanResult scanResult = dynamoDB.scan(scanRequest);
        return objectMapper.writeValueAsString(simplifyItems(scanResult.getItems()));
    }

    /**
     * PUT /sessions
     * Add client to session. Expected body: { "sessionId": "...", "clientId": "..." }
     * Updates the session record by setting its clientId field.
     */
    private String modifySession(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        String sessionId = (String) body.get("sessionId");
        String clientId = (String) body.get("clientId");
        if (sessionId == null || clientId == null) {
            throw new Exception("Missing sessionId or clientId in request body");
        }
        context.getLogger().log("Adding client " + clientId + " to session " + sessionId);

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("sessionId", new AttributeValue(sessionId));
        Map<String, AttributeValue> sessionItem = dynamoDB.getItem(new GetItemRequest().withTableName("session").withKey(key)).getItem();
        if (sessionItem == null || sessionItem.isEmpty()) {
            throw new Exception("Session not found");
        }
        if (!sessionItem.get("isOpen").getBOOL()) {
            throw new Exception("Cannot add client; session is private");
        }
        sessionItem.put("clientId", new AttributeValue(clientId));
        sessionItem.put("isOpen", new AttributeValue().withBOOL(false));
        dynamoDB.putItem(new PutItemRequest("session", sessionItem));
        return objectMapper.writeValueAsString(sessionItem);
    }

    /**
     * GET /sessions/public
     * Returns sessions where isOpen = true.
     */
    private String findOpenSessions(Context context) throws Exception {
        Map<String, AttributeValue> exprValues = new HashMap<>();
        exprValues.put(":io", new AttributeValue().withBOOL(true));
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("session")
                .withFilterExpression("isOpen = :io")
                .withExpressionAttributeValues(exprValues);
        ScanResult scanResult = dynamoDB.scan(scanRequest);
        return objectMapper.writeValueAsString(simplifyItems(scanResult.getItems()));
    }

    /**
     * POST /sessions/notes
     * Expected body: { "sessionId": "...", "userId": "...", "userRole": "...", "keyword": "..." }
     * Retrieves the session and returns its notes fields (privatenotes and sharedNotes).
     * If a keyword is provided and not found in the notes, returns an empty array.
     */
    // private String getOrSearchNotes(Map<String, Object> event, Context context) throws Exception {
    //     String bodyString = (String) event.get("body");
    //     Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
    //     String sessionId = body.get("sessionId") != null ? body.get("sessionId").toString() : null;
    //     String userId = (String) body.get("userId");
    //     String userRole = (String) body.get("userRole");
    //     String keyword = body.get("keyword") != null ? body.get("keyword").toString() : null;
    //     if (userId == null || userRole == null) {
    //         throw new Exception("Missing required fields in request body");
    //     }
    //     context.getLogger().log("Retrieving notes for session " + sessionId + " for user " + userId + " with role " + userRole);

    //     if (sessionId != null) {
    //         Map<String, AttributeValue> key = new HashMap<>();
    //         key.put("sessionId", new AttributeValue(sessionId));
    //         Map<String, AttributeValue> sessionItem = dynamoDB.getItem(new GetItemRequest().withTableName("session").withKey(key)).getItem();
    //         if (sessionItem == null || sessionItem.isEmpty()) {
    //             throw new Exception("Session not found");
    //         }
    //         String notes = "";
    //         if ("THERAPIST".equalsIgnoreCase(userRole)) {
    //             notes = sessionItem.get("privatenotes") != null ? sessionItem.get("privatenotes").getS() : "";
    //         } else if ("CLIENT".equalsIgnoreCase(userRole)) {
    //             notes = sessionItem.get("sharedNotes") != null ? sessionItem.get("sharedNotes").getS() : "";
    //         } else {
    //             throw new Exception("Invalid user role");
    //         }
    //         if (keyword != null && !notes.contains(keyword)) {
    //             return "[]";
    //         }
    //         List<Map<String, AttributeValue>> list = new ArrayList<>();
    //         list.add(sessionItem);
    //         return objectMapper.writeValueAsString(simplifyItems(list));
    //     }
    //     throw new Exception("Missing sessionId in request body");
    // }
    private String getOrSearchNotes(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        String sessionId = body.get("sessionId") != null ? body.get("sessionId").toString() : null;
        String userId = (String) body.get("userId");
        String userRole = (String) body.get("userRole");
        String keyword = body.get("keyword") != null ? body.get("keyword").toString() : null;
        
        if (userId == null || userRole == null) {
            throw new Exception("Missing required fields in request body (userId and userRole are required)");
        }
        context.getLogger().log("Retrieving notes for user " + userId + " with role " + userRole + 
                                 (sessionId != null ? (" for session " + sessionId) : " from all sessions") +
                                 (keyword != null ? (" filtering by keyword: " + keyword) : ""));
    
        List<Map<String, AttributeValue>> sessions = new ArrayList<>();
    
        // Case 1: If sessionId is provided, retrieve that session only.
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("sessionId", new AttributeValue(sessionId));
            Map<String, AttributeValue> sessionItem = dynamoDB.getItem(new GetItemRequest()
                    .withTableName("session")
                    .withKey(key)).getItem();
            if (sessionItem == null || sessionItem.isEmpty()) {
                throw new Exception("Session not found");
            }
            sessions.add(sessionItem);
        } else {
            // Case 2: sessionId not provided - scan for sessions where user is a participant.
            String filterExpression;
            Map<String, AttributeValue> exprValues = new HashMap<>();
            if ("THERAPIST".equalsIgnoreCase(userRole)) {
                // For therapists, search sessions where therapistId equals userId.
                filterExpression = "therapistId = :uid";
                exprValues.put(":uid", new AttributeValue(userId));
            } else if ("CLIENT".equalsIgnoreCase(userRole)) {
                // For clients, search sessions where clientId equals userId.
                filterExpression = "clientId = :uid";
                exprValues.put(":uid", new AttributeValue(userId));
            } else {
                throw new Exception("Invalid user role");
            }
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("session")
                    .withFilterExpression(filterExpression)
                    .withExpressionAttributeValues(exprValues);
            ScanResult scanResult = dynamoDB.scan(scanRequest);
            sessions = scanResult.getItems();
        }
    
        // Case 3: If a keyword is provided, filter the sessions further.
        if (keyword != null && !keyword.trim().isEmpty()) {
            List<Map<String, AttributeValue>> filteredSessions = new ArrayList<>();
            for (Map<String, AttributeValue> sessionItem : sessions) {
                String noteContent = "";
                if ("THERAPIST".equalsIgnoreCase(userRole)) {
                    String privateNotes = sessionItem.get("privatenotes") != null ? sessionItem.get("privatenotes").getS() : "";
                    String sharedNotes = sessionItem.get("sharedNotes") != null ? sessionItem.get("sharedNotes").getS() : "";
                    noteContent = privateNotes + " " + sharedNotes;
                } else if ("CLIENT".equalsIgnoreCase(userRole)) {
                    noteContent = sessionItem.get("sharedNotes") != null ? sessionItem.get("sharedNotes").getS() : "";
                }
                if (noteContent.toLowerCase().contains(keyword.toLowerCase())) {
                    filteredSessions.add(sessionItem);
                }
            }
            sessions = filteredSessions;
        }
        
        return objectMapper.writeValueAsString(simplifyItems(sessions));
    }
    

    // Helper method: Converts a list of DynamoDB items to a simplified structure (only string values)
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
