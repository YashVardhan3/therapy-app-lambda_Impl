package com.therapy.appointment;

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

public class AppointmentHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

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
            if ("/appointments".equals(path)) {
                if ("POST".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(201, createAppointment(event, context));
                } else if ("GET".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, getAppointments(event, context));
                } else if ("PUT".equalsIgnoreCase(httpMethod)) {
                    return formatResponse(200, updateAppointment(event, context));
                }
            }
            return formatResponse(400, "{\"message\": \"Invalid endpoint or method\"}");
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return formatResponse(500, "{\"message\": \"Error: " + e.getMessage() + "\"}");
        }
    }

    /**
     * POST /appointments: Create a new appointment
     * Expected JSON:
     * {
     *   "clientId": "...",
     *   "therapistId": "...",
     *   "appointmentDate": "2025-03-05T14:00:00Z",
     *   "sessionId": "..."  (Optional)
     * }
     */
    private String createAppointment(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);

        if (body.get("clientId") == null || body.get("therapistId") == null || body.get("appointmentDate") == null) {
            throw new Exception("Missing required fields in AppointmentRequest");
        }

        String appointmentId = UUID.randomUUID().toString();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("appointmentId", new AttributeValue(appointmentId));
        item.put("clientId", new AttributeValue(body.get("clientId").toString()));
        item.put("therapistId", new AttributeValue(body.get("therapistId").toString()));
        item.put("appointmentDate", new AttributeValue(body.get("appointmentDate").toString()));
        item.put("status", new AttributeValue(body.get("status").toString()));
        
        // Optional field: sessionId
        if (body.get("sessionId") != null) {
            item.put("sessionId", new AttributeValue(body.get("sessionId").toString()));
        }

        dynamoDB.putItem(new PutItemRequest("appointment", item));
        return objectMapper.writeValueAsString(item);
    }

    /**
     * GET /appointments: Retrieve appointments with optional filters
     * Supports filtering by: appointmentId, clientId, therapistId
     */
    private String getAppointments(Map<String, Object> event, Context context) throws Exception {
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        String filterExpression = "";
        Map<String, AttributeValue> exprValues = new HashMap<>();

        if (queryParams != null) {
            if (queryParams.get("appointmentId") != null) {
                String appointmentId = queryParams.get("appointmentId");
                Map<String, AttributeValue> key = new HashMap<>();
                key.put("appointmentId", new AttributeValue(appointmentId));
                Map<String, AttributeValue> result = dynamoDB.getItem(new GetItemRequest()
                        .withTableName("appointment")
                        .withKey(key)).getItem();
                if (result == null || result.isEmpty()) {
                    return "[]";
                }
                return objectMapper.writeValueAsString(result);
            }
            if (queryParams.get("clientId") != null) {
                filterExpression = "clientId = :clientId";
                exprValues.put(":clientId", new AttributeValue(queryParams.get("clientId")));
            }
            if (queryParams.get("therapistId") != null) {
                if (!filterExpression.isEmpty()) {
                    filterExpression += " AND ";
                }
                filterExpression += "therapistId = :therapistId";
                exprValues.put(":therapistId", new AttributeValue(queryParams.get("therapistId")));
            }
        }

        ScanRequest scanRequest = new ScanRequest().withTableName("appointment");
        if (!filterExpression.isEmpty()) {
            scanRequest.withFilterExpression(filterExpression).withExpressionAttributeValues(exprValues);
        }
        ScanResult scanResult = dynamoDB.scan(scanRequest);
        return objectMapper.writeValueAsString(scanResult.getItems());
    }

    /**
     * PUT /appointments: Update appointment details (date or status)
     * Expected JSON:
     * {
     *   "appointmentId": "...",
     *   "appointmentDate": "2025-03-05T14:00:00Z" (optional),
     *   "status": "CONFIRMED" or "CANCELLED" or "COMPLETED" (optional)
     * }
     */
    private String updateAppointment(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);

        String appointmentId = (String) body.get("appointmentId");
        if (appointmentId == null) {
            throw new Exception("Missing required field: appointmentId");
        }

        // Retrieve the existing appointment record
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("appointmentId", new AttributeValue(appointmentId));
        Map<String, AttributeValue> appointmentItem = dynamoDB.getItem(
                new GetItemRequest().withTableName("appointment").withKey(key)).getItem();
        if (appointmentItem == null || appointmentItem.isEmpty()) {
            throw new Exception("Invalid action. Must be 'ADD' or 'REMOVE'.");
        }

        // Update fields if provided
        if (body.get("appointmentDate") != null) {
            appointmentItem.put("appointmentDate", new AttributeValue(body.get("appointmentDate").toString()));
        }
        if (body.get("status") != null) {
            appointmentItem.put("status", new AttributeValue(body.get("status").toString()));
        }
        if (body.get("sessionId") != null) {
            appointmentItem.put("sessionId", new AttributeValue(body.get("sessionId").toString()));
        }
        dynamoDB.putItem(new PutItemRequest("appointment", appointmentItem));
        return objectMapper.writeValueAsString(appointmentItem);
    }

    // Helper method: Formats response
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
