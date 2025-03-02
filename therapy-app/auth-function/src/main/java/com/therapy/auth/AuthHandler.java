package com.therapy.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AuthHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()
    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
        "http://host.docker.internal:4566",  // ✅ Works inside Docker containers
        "ap-south-1"
    ))
    
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("Received event: " + event.toString());

        String path = (String) event.get("path");
        try {
            if ("/auth/register".equals(path)) {
                return formatResponse(201, registerUser(event, context));
            } else if ("/auth/login".equals(path)) {
                return formatResponse(200, loginUser(event, context));
            } else {
                return formatResponse(400, "Invalid path: " + path);
            }
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return formatResponse(500, "Error: " + e.getMessage());
        }
    }

    private String registerUser(Map<String, Object> event, Context context) throws Exception {
        context.getLogger().log("Inside registerUser");

        // Extracting body from event
        String bodyString = (String) event.get("body");
        context.getLogger().log("Received body: " + bodyString);

        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        String email = (String) body.get("email");
        String role = (String)body.get("role");
        String name = (String) body.get("name");
         context.getLogger().log("Extracted email: " + email);
        if (role.equalsIgnoreCase("client")) {
            Map<String, AttributeValue> item = new HashMap<>();
        String clientId = UUID.randomUUID().toString();
        item.put("name", new AttributeValue(name));
        
        item.put("clientId", new AttributeValue(clientId));
        item.put("email", new AttributeValue(email));
        item.put("password", new AttributeValue((String) body.get("password")));
        item.put("role", new AttributeValue("therapist"));
        // Insert into DynamoDB
        dynamoDB.putItem(new PutItemRequest("client", item));
        context.getLogger().log("User inserted into DynamoDB");
        }
        else if (role.equalsIgnoreCase("therapist")) {
            Map<String, AttributeValue> item = new HashMap<>();
        String therapistId = UUID.randomUUID().toString();
        item.put("name", new AttributeValue(name));
        
        item.put("therapistId", new AttributeValue(therapistId));
        item.put("email", new AttributeValue(email));
        item.put("password", new AttributeValue((String) body.get("password")));
        item.put("role", new AttributeValue("therapist"));
        // Insert into DynamoDB
        dynamoDB.putItem(new PutItemRequest("therapist", item));
        context.getLogger().log("User inserted into DynamoDB");
        }
        

        return "User registered successfully";
    }

    private String loginUser(Map<String, Object> event, Context context) throws Exception {
        context.getLogger().log("Inside loginUser");

        String bodyString = (String) event.get("body");
        context.getLogger().log("Received body: " + bodyString);

        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        String email = (String) body.get("email");

        context.getLogger().log("Extracted email: " + email);

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("clientId", new AttributeValue(email));

        // Query DynamoDB
        Map<String, AttributeValue> result = dynamoDB.getItem(new GetItemRequest()
                .withTableName("client")
                .withKey(key)).getItem();
        
        if (result == null) {
            context.getLogger().log("User not found in DB");
            return "Invalid email or password";
        }

        context.getLogger().log("User found, login successful");
        return "Login successful";
    }

    private Map<String, Object> formatResponse(int statusCode, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        //response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", "{\"message\": \"" + message + "\"}");
        return response;
    }
}
