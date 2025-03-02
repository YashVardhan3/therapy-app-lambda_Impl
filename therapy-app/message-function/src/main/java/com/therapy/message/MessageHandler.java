package com.therapy.message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MessageHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    // Connect to LocalStack DynamoDB; adjust endpoint if running in Docker.
    private final AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
            "http://host.docker.internal:4566", "ap-south-1"))
        .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("Received event: " + event);

        String httpMethod = (String) event.get("httpMethod");
        String path = (String) event.get("path");
        
        try {
            // Route based on method and path
            if ("POST".equalsIgnoreCase(httpMethod) && "/messages".equals(path)) {
                // Create a new message
                return formatResponse(201, createMessage(event, context));
            } else if ("GET".equalsIgnoreCase(httpMethod) && "/messages/conversation".equals(path)) {
                // Get conversation using query parameter conversationKey
                return formatResponse(200, getConversation(event, context));
            } else if ("GET".equalsIgnoreCase(httpMethod) && path.startsWith("/messages/all/")) {
                // Path format: /messages/all/{senderId}
                return formatResponse(200, getAllMessages(event, context));
            } else if ("GET".equalsIgnoreCase(httpMethod) && path.startsWith("/messages/")) {
                // Path format: /messages/{keyword} (ensure it does not conflict with above)
                return formatResponse(200, searchMessages(event, context));
            } else {
                return formatResponse(400, "{\"message\": \"Invalid request\"}");
            }
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return formatResponse(500, "{\"message\": \"Error: " + e.getMessage() + "\"}");
        }
    }

    // Create a new message: POST /messages
    private String createMessage(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        context.getLogger().log("CreateMessage - Body: " + bodyString);
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);

        
        String senderId = (String) body.get("senderId");
        String receiverId = (String) body.get("receiverId");
        String a[] = {senderId, receiverId};
        Arrays.sort(a);
        String conversationKey = a[0].concat("_").concat(a[1]);
        String content = (String) body.get("content");
        // If timestamp not provided, use current time in ISO-8601 format.
        String timestamp = body.get("timestamp") != null ? (String) body.get("timestamp") : new Date().toInstant().toString();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("conversationKey", new AttributeValue(conversationKey));
        item.put("timestamp", new AttributeValue(timestamp));
        item.put("senderId", new AttributeValue(senderId));
        item.put("receiverId", new AttributeValue(receiverId));
        item.put("content", new AttributeValue(content));

        dynamoDB.putItem(new PutItemRequest("message", item));
        // Return the created message as JSON
        return objectMapper.writeValueAsString(body);
    }

    // Get conversation: GET /messages/conversation?conversationKey=...
    private String getConversation(Map<String, Object> event, Context context) throws Exception {
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        if (queryParams == null || queryParams.get("conversationKey") == null) {
            throw new Exception("Missing query parameter: conversationKey");
        }
        String conversationKey = queryParams.get("conversationKey");
        context.getLogger().log("Fetching conversation for key: " + conversationKey);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":ck", new AttributeValue(conversationKey));

        QueryRequest queryRequest = new QueryRequest()
            .withTableName("message")
            .withKeyConditionExpression("conversationKey = :ck")
            .withExpressionAttributeValues(expressionValues);

        QueryResult queryResult = dynamoDB.query(queryRequest);
        return simplifyItems(queryResult.getItems());
    }

    private String simplifyItems(List<Map<String, AttributeValue>> items) throws Exception {
    List<Map<String, String>> simpleItems = new ArrayList<>();
    for (Map<String, AttributeValue> item : items) {
        Map<String, String> simpleItem = new HashMap<>();
        for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
            // Here, we assume attributes are stored as strings.
            // You can add more checks for numbers, booleans, etc.
            simpleItem.put(entry.getKey(), entry.getValue().getS());
        }
        simpleItems.add(simpleItem);
    }
    return objectMapper.writeValueAsString(simpleItems);
}

    // Get all messages for a sender: GET /messages/all/{senderId}
    private String getAllMessages(Map<String, Object> event, Context context) throws Exception {
        String path = (String) event.get("path"); // e.g., /messages/all/sender123
        String prefix = "/messages/all/";
        if (!path.startsWith(prefix)) {
            throw new Exception("Invalid path for getAllMessages");
        }
        String senderId = path.substring(prefix.length());
        context.getLogger().log("Fetching all messages for sender: " + senderId);

        // Scan with filter: senderId equals the given senderId
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":sid", new AttributeValue(senderId));

        ScanRequest scanRequest = new ScanRequest()
            .withTableName("message")
            .withFilterExpression("senderId = :sid")
            .withExpressionAttributeValues(expressionValues);

        ScanResult scanResult = dynamoDB.scan(scanRequest);
        return objectMapper.writeValueAsString(scanResult.getItems());
    }

    // Search messages by keyword in content: GET /messages/{keyword}
    private String searchMessages(Map<String, Object> event, Context context) throws Exception {
        String path = (String) event.get("path"); // e.g., /messages/hello
        String prefix = "/messages/";
        if (!path.startsWith(prefix)) {
            throw new Exception("Invalid path for searchMessages");
        }
        String keyword = path.substring(prefix.length());
        context.getLogger().log("Searching messages for keyword: " + keyword);

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":kw", new AttributeValue(keyword));

        // Use scan with contains() on content
        ScanRequest scanRequest = new ScanRequest()
            .withTableName("message")
            .withFilterExpression("contains(content, :kw)")
            .withExpressionAttributeValues(expressionValues);

        ScanResult scanResult = dynamoDB.scan(scanRequest);
        return objectMapper.writeValueAsString(scanResult.getItems());
    }

    private Map<String, Object> formatResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> responses = new HashMap<>();
        responses.put("Content-Type", "application/json");
        
        response.put("statusCode", statusCode);
        //response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("headers", responses);
        response.put("body", body);
        return response;
    }
}
