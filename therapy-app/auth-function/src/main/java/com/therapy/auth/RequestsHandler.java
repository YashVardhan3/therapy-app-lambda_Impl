package com.therapy.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;  // if needed later
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RequestsHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

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
            // Therapist Requests endpoints
            if (path.startsWith("/therapist-requests")) {
                if ("POST".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, createTherapistRequest(event, context));
                } else if ("GET".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, getTherapistRequests(event, context));
                } else if ("PUT".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, updateTherapistRequest(event, context));
                }
            } // Client Requests endpoints
            else if (path.startsWith("/client-requests")) {
                if ("POST".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, createClientRequest(event, context));
                } else if ("GET".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, getClientRequests(event, context));
                } else if ("PUT".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, updateClientRequest(event, context));
                }
            }
            return formatResponse(400, "{\"message\": \"Invalid endpoint or method\"}");
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return formatResponse(500, "{\"message\": \"Error: " + e.getMessage() + "\"}");
        }
    }

    // ----- Therapist Requests Methods -----
    // POST /therapist-requests
    // Expected JSON:
    // {
    //   "type": "MAPPING" or "JOURNAL_ACCESS",
    //   "therapistId": "...",
    //   "clientId": "...",
    //   "journalId": "..." (optional),
    //   "message": "..."
    // }
    private String createTherapistRequest(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);

        String type = (String) body.get("type");
        String therapistId = (String) body.get("therapistId");
        String clientId = (String) body.get("clientId");
        String journalId = body.get("journalId") != null ? body.get("journalId").toString() : null;
        String message = body.get("message") != null ? body.get("message").toString() : "";

        if (type == null || therapistId == null || clientId == null) {
            throw new Exception("Missing required fields in TherapistRequest");
        }

        String requestId = UUID.randomUUID().toString();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("requestId", new AttributeValue(requestId));
        item.put("type", new AttributeValue(type));
        item.put("therapistId", new AttributeValue(therapistId));
        item.put("clientId", new AttributeValue(clientId));
        if (journalId != null) {
            item.put("journalId", new AttributeValue(journalId));
        }
        item.put("message", new AttributeValue(message));
        item.put("status", new AttributeValue("PENDING"));

        dynamoDB.putItem(new PutItemRequest("therapistRequests", item));
        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("requestId", requestId);
        responseMap.put("status", "PENDING");
        return objectMapper.writeValueAsString(responseMap);
    }

    // GET /therapist-requests with optional filters: therapistId, clientId, status.
    // private String getTherapistRequests(Map<String, Object> event, Context context) throws Exception {
    //     Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
    //     String filterExpression = "";
    //     Map<String, AttributeValue> exprValues = new HashMap<>();

    //     if (queryParams != null) {
    //         if (queryParams.get("therapistId") != null) {
    //             filterExpression = "therapistId = :tid";
    //             exprValues.put(":tid", new AttributeValue(queryParams.get("therapistId")));
    //         }
    //         if (queryParams.get("clientId") != null) {
    //             if (!filterExpression.isEmpty()) {
    //                 filterExpression += " and ";
    //             }
    //             filterExpression += "clientId = :cid";
    //             exprValues.put(":cid", new AttributeValue(queryParams.get("clientId")));
    //         }
    //         if (queryParams.get("status") != null) {
    //             if (!filterExpression.isEmpty()) {
    //                 filterExpression += " and ";
    //             }
    //             filterExpression += "status = :st";
    //             exprValues.put(":st", new AttributeValue(queryParams.get("status")));
    //         }
    //     }
    //     ScanRequest scanRequest = new ScanRequest().withTableName("therapistRequests");
    //     if (!filterExpression.isEmpty()) {
    //         scanRequest.withFilterExpression(filterExpression).withExpressionAttributeValues(exprValues);
    //     }
    //     ScanResult scanResult = dynamoDB.scan(scanRequest);
    //     return objectMapper.writeValueAsString(simplifyItems(scanResult.getItems()));
    // }
    private String getTherapistRequests(Map<String, Object> event, Context context) throws Exception {
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        String filterExpression = "";
        Map<String, AttributeValue> exprValues = new HashMap<>();
        Map<String, String> exprAttrNames = new HashMap<>();
    
        if (queryParams != null) {
            if (queryParams.get("therapistId") != null) {
                // If you expect all items to have therapistId, you could add an attribute_exists check:
                // filterExpression = "attribute_exists(therapistId) and therapistId = :tid";
                // exprValues.put(":tid", new AttributeValue(queryParams.get("therapistId")));
                filterExpression += "#st = :st";
                exprValues.put(":st", new AttributeValue(queryParams.get("therapistId")));
                exprAttrNames.put("#st", "therapistId");    
            }
            if (queryParams.get("clientId") != null) {
                if (!filterExpression.isEmpty()) {
                    filterExpression += " and ";
                }
                // filterExpression += "attribute_exists(clientId) and clientId = :cid";
                // exprValues.put(":cid", new AttributeValue(queryParams.get("clientId")));
                filterExpression += "#st = :st";
                exprValues.put(":st", new AttributeValue(queryParams.get("clientId")));
                exprAttrNames.put("#st", "clientId");
            }
            if (queryParams.get("status") != null) {
                if (!filterExpression.isEmpty()) {
                    filterExpression += " and ";
                }
                // Use alias for reserved word "status"
                filterExpression += "#st = :st";
                exprValues.put(":st", new AttributeValue(queryParams.get("status")));
                exprAttrNames.put("#st", "status");
            }
        }
    
        // Debug logging: print the final expressions.
        context.getLogger().log("Filter Expression: " + filterExpression);
        context.getLogger().log("Expression Attribute Values: " + exprValues);
        context.getLogger().log("Expression Attribute Names: " + exprAttrNames);
    
        ScanRequest scanRequest = new ScanRequest().withTableName("therapistRequests");
        if (!filterExpression.isEmpty()) {
            scanRequest.withFilterExpression(filterExpression)
                       .withExpressionAttributeValues(exprValues)
                       .withExpressionAttributeNames(exprAttrNames);
        }
        ScanResult scanResult = dynamoDB.scan(scanRequest);
        return objectMapper.writeValueAsString(simplifyItems(scanResult.getItems()));
    }
    
    


    // PUT /therapist-requests: Update status (approve or reject)
    // Expected JSON: { "requestId": "...", "status": "APPROVED" or "REJECTED" }
    private String updateTherapistRequest(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        String requestId = (String) body.get("requestId");
        String status = (String) body.get("status");
        if (requestId == null || status == null) {
            throw new Exception("Missing required fields in ClientRequest update");
        }
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("requestId", new AttributeValue(requestId));
        Map<String, AttributeValue> requestItem = dynamoDB.getItem(new GetItemRequest()
                .withTableName("clientRequests")
                .withKey(key)).getItem();
        if (requestItem == null || requestItem.isEmpty()) {
            throw new Exception("Client request not found");
        }
        if (status.equals("APPROVED")) {
            if (requestItem.get("clientId") == null || requestItem.get("therapistId") == null || requestItem.get("requestedDate") == null) {
                throw new Exception("Missing required fields in AppointmentRequest");
            }

            String appointmentId = UUID.randomUUID().toString();

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("appointmentId", new AttributeValue(appointmentId));
            item.put("clientId", new AttributeValue(requestItem.get("clientId").toString()));
            item.put("therapistId", new AttributeValue(requestItem.get("therapistId").toString()));
            item.put("appointmentDate", new AttributeValue(requestItem.get("requestedDate").toString()));
            item.put("status", new AttributeValue("CONFIRMED"));

            dynamoDB.putItem(new PutItemRequest("appointment", item));
        }

        requestItem.put("status", new AttributeValue(status));
        dynamoDB.putItem(new PutItemRequest("clientRequests", requestItem));
        return "{\"message\": \"Client request updated successfully\"}";
    }

    // ----- Client Requests Methods -----
    // POST /client-requests: Create a new client request for an appointment.
    // Expected JSON: { "clientId": "...", "therapistId": "...", "requestedDate": "date-string", "message": "..." }
    private String createClientRequest(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        String clientId = (String) body.get("clientId");
        String therapistId = (String) body.get("therapistId");
        String requestedDate = (String) body.get("requestedDate");
        String message = body.get("message") != null ? body.get("message").toString() : "";
        if (clientId == null || therapistId == null || requestedDate == null) {
            throw new Exception("Missing required fields in ClientRequest");
        }
        String requestId = UUID.randomUUID().toString();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("requestId", new AttributeValue(requestId));
        item.put("clientId", new AttributeValue(clientId));
        item.put("therapistId", new AttributeValue(therapistId));
        item.put("requestedDate", new AttributeValue(requestedDate));
        item.put("message", new AttributeValue(message));
        item.put("status", new AttributeValue("PENDING"));

        dynamoDB.putItem(new PutItemRequest("clientRequests", item));
        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("requestId", requestId);
        responseMap.put("status", "PENDING");
        return objectMapper.writeValueAsString(responseMap);
    }

    // GET /client-requests: Retrieve client requests with optional filters: clientId, therapistId, status.
    private String getClientRequests(Map<String, Object> event, Context context) throws Exception {
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        String filterExpression = "";
        Map<String, AttributeValue> exprValues = new HashMap<>();
        //Map<String, AttributeValue> exprValues = new HashMap<>();
        Map<String, String> exprAttrNames = new HashMap<>();
        if (queryParams != null) {
            if (queryParams.get("therapistId") != null) {
                // If you expect all items to have therapistId, you could add an attribute_exists check:
                // filterExpression = "attribute_exists(therapistId) and therapistId = :tid";
                // exprValues.put(":tid", new AttributeValue(queryParams.get("therapistId")));
                filterExpression += "#st = :st";
                exprValues.put(":st", new AttributeValue(queryParams.get("therapistId")));
                exprAttrNames.put("#st", "therapistId");    
            }
            if (queryParams.get("clientId") != null) {
                if (!filterExpression.isEmpty()) {
                    filterExpression += " and ";
                }
                // filterExpression += "attribute_exists(clientId) and clientId = :cid";
                // exprValues.put(":cid", new AttributeValue(queryParams.get("clientId")));
                filterExpression += "#st = :st";
                exprValues.put(":st", new AttributeValue(queryParams.get("clientId")));
                exprAttrNames.put("#st", "clientId");
            }
            if (queryParams.get("status") != null) {
                if (!filterExpression.isEmpty()) {
                    filterExpression += " and ";
                }
                // Use alias for reserved word "status"
                filterExpression += "#st = :st";
                exprValues.put(":st", new AttributeValue(queryParams.get("status")));
                exprAttrNames.put("#st", "status");
            }
        }
        ScanRequest scanRequest = new ScanRequest().withTableName("clientRequests");
        if (!filterExpression.isEmpty()) {
            scanRequest.withFilterExpression(filterExpression).withExpressionAttributeValues(exprValues).withExpressionAttributeNames(exprAttrNames);
        }
        ScanResult scanResult = dynamoDB.scan(scanRequest);
        return objectMapper.writeValueAsString(simplifyItems(scanResult.getItems()));
    }

    // PUT /client-requests: Update a client request's status.
    // Expected JSON: { "requestId": "...", "status": "APPROVED" or "REJECTED" }
    private String updateClientRequest(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        String requestId = (String) body.get("requestId");
        String status = (String) body.get("status");
        if (requestId == null || status == null) {
            throw new Exception("Missing required fields in TherapistRequest update");
        }
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("requestId", new AttributeValue(requestId));
        Map<String, AttributeValue> requestItem = dynamoDB.getItem(new GetItemRequest()
                .withTableName("therapistRequests")
                .withKey(key)).getItem();
        if (requestItem == null || requestItem.isEmpty()) {
            throw new Exception("Therapist request not found");
        }
        if (requestItem.get("status").getS().equalsIgnoreCase("APPROVED")) {
            
        }

        requestItem.put("status", new AttributeValue(status));
        dynamoDB.putItem(new PutItemRequest("therapistRequests", requestItem));
        return "{\"message\": \"Therapist request updated successfully\"}";
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
