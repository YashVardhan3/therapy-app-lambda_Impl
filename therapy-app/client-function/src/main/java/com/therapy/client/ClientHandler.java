// package com.therapy.client;

// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;

// import com.amazonaws.client.builder.AwsClientBuilder;
// import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
// import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
// import com.amazonaws.services.dynamodbv2.model.AttributeValue;
// import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
// import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
// import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
// import com.amazonaws.services.lambda.runtime.Context;
// import com.amazonaws.services.lambda.runtime.RequestHandler;
// import com.fasterxml.jackson.databind.ObjectMapper;

// public class ClientHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

//     private final AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()
//         .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
//             "http://host.docker.internal:4566", "ap-south-1"))
//         .build();

//     private final ObjectMapper objectMapper = new ObjectMapper();

//     @Override
//     public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
//         context.getLogger().log("Received event: " + event);

//         String httpMethod = (String) event.get("httpMethod");
//         String path = (String) event.get("path");
        
//         try {
//             // Route based on method and path
//             if ("GET".equalsIgnoreCase(httpMethod) && "/user/get".equals(path)) {
//                 // Create a new message
//                 return formatResponse(201, getUserDetails(event, context));
//             }  else if ("PUT".equalsIgnoreCase(httpMethod) && path.startsWith("/user/get")) {
//                 // Path format: /messages/all/{senderId}
//                 return formatResponse(200, updateUserDetails(event, context));
//             } else if ("POST".equalsIgnoreCase(httpMethod) && path.startsWith("/user/delete")) {
//                 // Path format: /messages/{keyword} (ensure it does not conflict with above)
//                 return formatResponse(200, deleteUser(event, context));
//             }
//             else if ("POST".equalsIgnoreCase(httpMethod) && path.startsWith("/mapping")) {
//                 // Path format: /messages/{keyword} (ensure it does not conflict with above)
//                 return formatResponse(200, mappingTherapist(event, context));
//             }
            
//             else {
//                 return formatResponse(400, "{\"message\": \"Invalid request\"}");
//             }
//         } catch (Exception e) {
//             context.getLogger().log("Error: " + e.getMessage());
//             return formatResponse(500, "{\"message\": \"Error: " + e.getMessage() + "\"}");
//         }
//     }

//     // Create a new message: POST /messages
//     private String getUserDetails(Map<String, Object> event, Context context) throws Exception {
//         Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
//         if (queryParams == null || queryParams.get("userId") == null) {
//             throw new Exception("Missing query parameter: userId");
//         }
//         String userId = queryParams.get("userId");
//         context.getLogger().log("Fetching user for userId: " + userId);

//         Map<String, AttributeValue> key = new HashMap<>();
//         key.put(":userId", new AttributeValue(userId));

//         Map<String, AttributeValue> result = dynamoDB.getItem(
//             new GetItemRequest().withTableName("client").withKey(key)).getItem();
//         if (result==null) {
//             result = dynamoDB.getItem(
//             new GetItemRequest().withTableName("therapist").withKey(key)).getItem();        
//             }
//         if (result==null||result.isEmpty()) {
//             return "{\"message\": \"User not found\"}";
//         }
//         Map<String, Object> responseMap = new HashMap<>();
//         if (result.get("role").getS().equals("CLIENT")) {
            
//             responseMap.put("userId", result.get("clientId").getS());
//             if (result.get("name").getS()!=null) {
//                 responseMap.put("name", result.get("name").getS());
            
//             } else responseMap.put("name", "");
             
//             if (result.get("email").getS()!=null) {
//                 responseMap.put("email", result.get("email").getS());
            
//             } else responseMap.put("email", "");
//             if (result.get("description").getS()!=null) {
//                 responseMap.put("description", result.get("description").getS());
            
//             } else responseMap.put("description", "");
//             if (!result.get("therapists").getL().isEmpty()) {
//                 responseMap.put("therapists", result.get("therapists").getL());
//             } else responseMap.put("therapists", new ArrayList<AttributeValue>());

//         }
//         else if (result.get("role").getS().equals("THERAPIST")) {
//             responseMap.put("userId", result.get("therapistId").getS());
//             if (result.get("name").getS()!=null) {
//                 responseMap.put("name", result.get("name").getS());
            
//             } else responseMap.put("name", "");
             
//             if (result.get("email").getS()!=null) {
//                 responseMap.put("email", result.get("email").getS());
            
//             } else responseMap.put("email", "");
//             if (!result.get("specialization").getL().isEmpty()) {
//                 responseMap.put("specialization", result.get("specialization").getL());
//             } else responseMap.put("therapists", new ArrayList<AttributeValue>());

//             if (!result.get("availableSlots").getL().isEmpty()) {
//                 responseMap.put("availableSlots", result.get("availableSlots").getL());
//             } else responseMap.put("availableSlots", new ArrayList<AttributeValue>());


//         }

        
//         return objectMapper.writeValueAsString(responseMap);
        
//     }


//     private String updateUserDetails(Map<String, Object> event, Context context) throws Exception {
//         String bodyString = (String) event.get("body");
//         context.getLogger().log("CreateMessage - Body: " + bodyString);
//         Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
//         String userId =(String) body.get("userId");
//         context.getLogger().log("Fetching conversation for key: " + userId);
        
        
//         context.getLogger().log("Fetching user for userId: " + userId);

//         Map<String, AttributeValue> key = new HashMap<>();
//         key.put(":userId", new AttributeValue(userId));

//         Map<String, AttributeValue> result = dynamoDB.getItem(
//             new GetItemRequest().withTableName("client").withKey(key)).getItem();
//         if (result==null) {
//             result = dynamoDB.getItem(
//             new GetItemRequest().withTableName("therapist").withKey(key)).getItem();        
//             }
//         if (result==null||result.isEmpty()) {
//             return "{\"message\": \"User not found\"}";
//         }


//         Map<String, AttributeValue> newItem = new HashMap<>();
//         newItem.putAll(key);
//         if (result.get("role").getS().equals("CLIENT")) {

            
//             AttributeValue name = new AttributeValue((String) body.get("name"));
//             AttributeValue email = new AttributeValue((String) body.get("email"));
//             AttributeValue password =new AttributeValue((String) body.get("password"));
//             AttributeValue description =new AttributeValue((String) body.get("description"));
//             AttributeValue therapist = new AttributeValue((ArrayList<String>) body.get("therapists"));

            
//             newItem.put("clientId", result.get("clientId"));
//             if (result.get("name").getS()!=null) {
//                 newItem.put("name", name);
            
//             } 
//             if ((result.get("email").getS()!=null)&&(result.get("password").getS().equals(password.getS()))) {
//                 newItem.put("email", email);
            
//             } 
//             if (result.get("description").getS()!=null) {
//                 newItem.put("description", description);
            
//             } 
//             if (!result.get("therapists").getL().isEmpty()) {
//                 newItem.put("therapists", therapist);
//             }
//             dynamoDB.putItem(new PutItemRequest("client", newItem));
        
//         }
//         else if (result.get("role").getS().equals("THERAPIST")) {
//             AttributeValue name = new AttributeValue((String) body.get("name"));
//             AttributeValue email = new AttributeValue((String) body.get("email"));
//             AttributeValue password =new AttributeValue((String) body.get("password"));
//             AttributeValue clients = new AttributeValue((ArrayList<String>) body.get("therapists"));
//             AttributeValue specialization = new AttributeValue((ArrayList<String>) body.get("specialization"));
//             AttributeValue availableSlot = new AttributeValue((ArrayList<String>) body.get("availableSlots"));
            


//             newItem.put("therapistId", result.get("therapistId"));
//             if (result.get("name").getS()!=null) {
//                 newItem.put("name", name);
            
//             }
//             if ((result.get("email").getS()!=null)&&(result.get("password").getS().equals(password.getS()))) {
//                 newItem.put("email", email);
            
//             }
//             if (!result.get("clients").getL().isEmpty()) {
//                 newItem.put("clients", clients);
//             } 


//             if (!result.get("specialization").getL().isEmpty()) {
//                 newItem.put("specialization", specialization);
//             } 
//             if (!result.get("availableSlots").getL().isEmpty()) {
//                 newItem.put("availableSlots", availableSlot);
//             } 
//             dynamoDB.putItem(new PutItemRequest("therapist", newItem));
        
//         }

//         return objectMapper.writeValueAsString(newItem);
//     }

//     private String mappingTherapist(Map<String, Object> event, Context context) throws Exception {
//         String bodyString = (String) event.get("body");
//         Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        
//         String therapistId = body.get("therapistId").toString();
//         String clientId = body.get("clientId").toString();
//         String action = body.get("action").toString();
//         if (therapistId == null || clientId == null || action == null) {
//             return "{\"message\": \"Missing required fields\"}";
//         }
//         context.getLogger().log("Mapping action: " + action + ", therapistId: " + therapistId + ", clientId: " + clientId);
        
//         Map<String, AttributeValue> key = new HashMap<>();
//         key.put(":clientId", new AttributeValue(clientId));

//         Map<String, AttributeValue> clientItem = dynamoDB.getItem(new GetItemRequest().withTableName("client").withKey(key)).getItem();
//         if (clientItem == null || clientItem.isEmpty()) {
//             return "{\"message\": \"Client not found\"}";
//         }

//         // Get the existing clients list (if present)
//         List<AttributeValue> therapists = new ArrayList<>();
//         if (clientItem.get("therapists") != null) {
//             therapists = clientItem.get("therapists").getL();
//         }
//         boolean modified = false;
//         if ("MAP".equalsIgnoreCase(action)) {
//             // Add clientId if not already in the list
//             boolean exists = therapists.stream().anyMatch(av -> therapistId.equals(av.getS()));
//             if (!exists) {
//                 therapists.add(new AttributeValue(therapistId));
//                 modified = true;
//             }
//         } else if ("UNMAP".equalsIgnoreCase(action)) {
//             int initialSize = therapists.size();
//             therapists.removeIf(av -> therapistId.equals(av.getS()));
//             if (therapists.size() != initialSize) {
//                 modified = true;
//             }
//         } else {
//             return "{\"message\": \"Invalid action\"}";
//         }
//         if (modified) {
//             clientItem.put("therapists", new AttributeValue().withL(therapists));
//             dynamoDB.putItem(new PutItemRequest("client", clientItem));
//         } else {
//             return "{\"message\": \"No changes made\"}";
//         }


//         return "{\"message\": \"Mapping modified successfully\"}";
//     }


//     private String deleteUser(Map<String, Object> event, Context context) throws Exception {
//         String bodyString = (String) event.get("body");
//         Map<String, String> body = objectMapper.readValue(bodyString, Map.class);
//         String userId = body.get("userId");
//         String email = body.get("email");
//         String password = body.get("password");
//         if (userId==null||email == null || password == null) {
//             return "{\"message\": \"Missing email or password\"}";
//         }
//         context.getLogger().log("Deleting user with email: " + email);
        

//         Map<String, AttributeValue> key = new HashMap<>();
//         key.put(":userId", new AttributeValue(userId));

//         Map<String, AttributeValue> userItem = dynamoDB.getItem(
//             new GetItemRequest().withTableName("client").withKey(key)).getItem();



//         if (userItem==null) {
//             userItem = dynamoDB.getItem(
//             new GetItemRequest().withTableName("therapist").withKey(key)).getItem();        
//             }
//         if (userItem==null||userItem.isEmpty()) {
//             return "{\"message\": \"User not found\"}";
//         }
//         if (userItem.get("role").getS().equals("CLIENT")) {
//             String storedPassword = userItem.get("password").getS();
//             if (password.equals(storedPassword)) {
//                 return "{\"message\": \"Invalid password\"}";
//                 }
        
//         // Delete user from client table using clientId key.
//             Map<String, AttributeValue> key1 = new HashMap<>();
//             key1.put("clientId", userItem.get("clientId"));
//             dynamoDB.deleteItem(new DeleteItemRequest().withTableName("client").withKey(key1));
        
//         }
//         if (userItem.get("role").getS().equals("THERAPIST")) {
//             String storedPassword = userItem.get("password").getS();
//             if (password.equals(storedPassword)) {
//                 return "{\"message\": \"Invalid password\"}";
//                 }
        
//         // Delete user from client table using clientId key.
//             Map<String, AttributeValue> key1 = new HashMap<>();
//             key1.put("therapistId", userItem.get("therapistId"));
//             dynamoDB.deleteItem(new DeleteItemRequest().withTableName("therapist").withKey(key1));
        
//         }

//         return "User Deleted";
//     }

//     private Map<String, Object> formatResponse(int statusCode, String body) {
//         Map<String, Object> response = new HashMap<>();
//         Map<String, Object> responses = new HashMap<>();
//         responses.put("Content-Type", "application/json");
        
//         response.put("statusCode", statusCode);
//         //response.put("headers", Map.of("Content-Type", "application/json"));
//         response.put("headers", responses);
//         response.put("body", body);
//         return response;
//     }
// }



package com.therapy.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ClientHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

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
            if ("GET".equalsIgnoreCase(httpMethod) && "/user/get".equals(path)) {
                return formatResponse(200, getUserDetails(event, context));
            } else if ("PUT".equalsIgnoreCase(httpMethod) && path.startsWith("/user/get")) {
                return formatResponse(200, updateUserDetails(event, context));
            } else if ("POST".equalsIgnoreCase(httpMethod) && path.startsWith("/user/delete")) {
                return formatResponse(200, deleteUser(event, context));
            } else if ("POST".equalsIgnoreCase(httpMethod) && path.startsWith("/mapping")) {
                return formatResponse(200, mappingTherapist(event, context));
            } else {
                return formatResponse(400, "{\"message\": \"Invalid request\"}");
            }
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return formatResponse(500, "{\"message\": \"Error: " + e.getMessage() + "\"}");
        }
    }

    // Helper method to retrieve a user (tries client table, then therapist table)
    private Map<String, AttributeValue> retrieveUser(String userId, Context context) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("clientId", new AttributeValue(userId));
        Map<String, AttributeValue> user = dynamoDB.getItem(new GetItemRequest()
                                        .withTableName("client")
                                        .withKey(key)).getItem();
        user.put("role", new AttributeValue("client"));                                
        if (user == null || user.isEmpty()) {
            key.clear();
            key.put("therapistId", new AttributeValue(userId));
            user = dynamoDB.getItem(new GetItemRequest()
                                        .withTableName("therapist")
                                        .withKey(key)).getItem();
                                        user.put("role", new AttributeValue("therapist"));                               
        }
        return user;
    }

    // GET /user/get?userId=...
    private String getUserDetails(Map<String, Object> event, Context context) throws Exception {
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        if (queryParams == null || queryParams.get("userId") == null) {
            throw new Exception("Missing query parameter: userId");
        }
        String userId = queryParams.get("userId");
        context.getLogger().log("Fetching user for userId: " + userId);

        Map<String, AttributeValue> userItem = retrieveUser(userId, context);
        if (userItem == null || userItem.isEmpty()) {
            return "{\"message\": \"User not found\"}";
        }
        
        // Build response based on role
        String role = userItem.get("role") != null ? userItem.get("role").getS() : "";
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("role", role);
        if ("CLIENT".equalsIgnoreCase(role)) {
            responseMap.put("userId", userItem.get("clientId").getS());
        } else if ("THERAPIST".equalsIgnoreCase(role)) {
            responseMap.put("userId", userItem.get("therapistId").getS());
        }
        responseMap.put("name", userItem.get("name") != null ? userItem.get("name").getS() : "");
        responseMap.put("email", userItem.get("email") != null ? userItem.get("email").getS() : "");
        responseMap.put("description", userItem.get("description") != null ? userItem.get("description").getS() : "");
        
        // For lists, use empty list if null
        if ("CLIENT".equalsIgnoreCase(role)) {
            responseMap.put("therapists", userItem.get("therapists") != null ? userItem.get("therapists").getL() : new ArrayList<>());
        } else if ("THERAPIST".equalsIgnoreCase(role)) {
            responseMap.put("specialization", userItem.get("specialization") != null ? userItem.get("specialization").getL() : new ArrayList<>());
            responseMap.put("availableSlots", userItem.get("availableSlots") != null ? userItem.get("availableSlots").getL() : new ArrayList<>());
            responseMap.put("clients", userItem.get("clients") != null ? userItem.get("clients").getL() : new ArrayList<>());
        }
        
        return objectMapper.writeValueAsString(responseMap);
    }

    // PUT /user/get?userId=... with body as UserUpdateRequest
    private String updateUserDetails(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        context.getLogger().log("UpdateUser - Body: " + bodyString);
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        String userId = (String) body.get("userId");
        if (userId == null) {
            throw new Exception("Missing userId in body");
        }
        context.getLogger().log("Updating user with userId: " + userId);

        Map<String, AttributeValue> existingItem = retrieveUser(userId, context);
        if (existingItem == null || existingItem.isEmpty()) {
            return "{\"message\": \"User not found\"}";
        }
        String role = existingItem.get("role") != null ? existingItem.get("role").getS() : "";
        String tableName = "CLIENT".equalsIgnoreCase(role) ? "client" : "therapist";

        // Validate provided password matches stored password (if provided)
        if (body.get("password") != null && existingItem.get("password") != null) {
            String providedPassword = (String) body.get("password");
            String storedPassword = existingItem.get("password").getS();
            if (!providedPassword.equals(storedPassword)) {
                return "{\"message\": \"Invalid password\"}";
            }
        }
        
        // Update fields if provided
        if (body.get("name") != null) {
            existingItem.put("name", new AttributeValue((String) body.get("name")));
        }
        if (body.get("email") != null) {
            existingItem.put("email", new AttributeValue((String) body.get("email")));
        }
        if (body.get("description") != null) {
            existingItem.put("description", new AttributeValue((String) body.get("description")));
        }
        // For CLIENT: update therapists list if provided
        if ("CLIENT".equalsIgnoreCase(role) && body.get("therapists") != null) {
            List<String> therapistList = (List<String>) body.get("therapists");
            List<AttributeValue> therapistAVList = new ArrayList<>();
            for (String t : therapistList) {
                therapistAVList.add(new AttributeValue(t));
            }
            existingItem.put("therapists", new AttributeValue().withL(therapistAVList));
        }
        // For THERAPIST: update specialization and availableSlots if provided
        if ("THERAPIST".equalsIgnoreCase(role)) {
            if (body.get("specialization") != null) {
                List<String> specList = (List<String>) body.get("specialization");
                List<AttributeValue> specAVList = new ArrayList<>();
                for (String s : specList) {
                    specAVList.add(new AttributeValue(s));
                }
                existingItem.put("specialization", new AttributeValue().withL(specAVList));
            }
            if (body.get("availableSlots") != null) {
                List<String> slotsList = (List<String>) body.get("availableSlots");
                List<AttributeValue> slotsAVList = new ArrayList<>();
                for (String slot : slotsList) {
                    slotsAVList.add(new AttributeValue(slot));
                }
                existingItem.put("availableSlots", new AttributeValue().withL(slotsAVList));
            }
            if (body.get("clients") != null) {
                List<String> specList = (List<String>) body.get("clients");
                List<AttributeValue> specAVList = new ArrayList<>();
                for (String s : specList) {
                    specAVList.add(new AttributeValue(s));
                }
                existingItem.put("clients", new AttributeValue().withL(specAVList));
            }
        }
        
        // Write updated item back to the table
        dynamoDB.putItem(new PutItemRequest(tableName, existingItem));
        return objectMapper.writeValueAsString(existingItem);
    }

    // POST /mapping with body { "therapistId": "...", "clientId": "...", "action": "MAP" or "UNMAP" }
    private String mappingTherapist(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, Object> body = objectMapper.readValue(bodyString, Map.class);
        
        String therapistId = body.get("therapistId").toString();
        String clientId = body.get("clientId").toString();
        String action = body.get("action").toString();
        if (therapistId == null || clientId == null || action == null) {
            return "{\"message\": \"Missing required fields\"}";
        }
        context.getLogger().log("Mapping action: " + action + ", therapistId: " + therapistId + ", clientId: " + clientId);
        
        // Retrieve client record from "client" table using clientId
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("clientId", new AttributeValue(clientId));
        Map<String, AttributeValue> clientItem = dynamoDB.getItem(
                new GetItemRequest().withTableName("client").withKey(key))
                .getItem();
        if (clientItem == null || clientItem.isEmpty()) {
            return "{\"message\": \"Client not found\"}";
        }

        // Retrieve existing therapists list if any
        List<AttributeValue> therapists = new ArrayList<>();
        if (clientItem.get("therapists") != null && clientItem.get("therapists").getL() != null) {
            therapists = clientItem.get("therapists").getL();
        }
        boolean modified = false;
        if ("MAP".equalsIgnoreCase(action)) {
            boolean exists = therapists.stream().anyMatch(av -> therapistId.equals(av.getS()));
            if (!exists) {
                therapists.add(new AttributeValue(therapistId));
                modified = true;
            }
        } else if ("UNMAP".equalsIgnoreCase(action)) {
            int initialSize = therapists.size();
            therapists.removeIf(av -> therapistId.equals(av.getS()));
            if (therapists.size() != initialSize) {
                modified = true;
            }
        } else {
            return "{\"message\": \"Invalid action\"}";
        }
        if (modified) {
            clientItem.put("therapists", new AttributeValue().withL(therapists));
            dynamoDB.putItem(new PutItemRequest("client", clientItem));
            return "{\"message\": \"Mapping modified successfully\"}";
        } else {
            return "{\"message\": \"No changes made\"}";
        }
    }

    // POST /user/delete with body { "userId": "...", "email": "...", "password": "..." }
    private String deleteUser(Map<String, Object> event, Context context) throws Exception {
        String bodyString = (String) event.get("body");
        Map<String, String> body = objectMapper.readValue(bodyString, Map.class);
        String userId = body.get("userId");
        String email = body.get("email");
        String password = body.get("password");
        if (userId == null || email == null || password == null) {
            return "{\"message\": \"Missing userId, email or password\"}";
        }
        context.getLogger().log("Deleting user with email: " + email);

        // Try client table first
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("clientId", new AttributeValue(userId));
        Map<String, AttributeValue> userItem = dynamoDB.getItem(
            new GetItemRequest().withTableName("client").withKey(key)).getItem();
        String tableName = "client";
        if (userItem == null || userItem.isEmpty()) {
            // Try therapist table
            key.clear();
            key.put("therapistId", new AttributeValue(userId));
            userItem = dynamoDB.getItem(
                new GetItemRequest().withTableName("therapist").withKey(key)).getItem();
            tableName = "therapist";
        }
        if (userItem == null || userItem.isEmpty()) {
            return "{\"message\": \"User not found\"}";
        }
        String storedPassword = userItem.get("password").getS();
        if (!(password.equals(storedPassword))) {
            return "{\"message\": \"Invalid password\"}";
        }
        // Delete user from the appropriate table
        Map<String, AttributeValue> deleteKey = new HashMap<>();
        if ("CLIENT".equalsIgnoreCase(userItem.get("role").getS())) {
            deleteKey.put("clientId", userItem.get("clientId"));
        } else {
            deleteKey.put("therapistId", userItem.get("therapistId"));
        }
        dynamoDB.deleteItem(new DeleteItemRequest().withTableName(tableName).withKey(deleteKey));
        
        return "{\"message\": \"User Deleted\"}";
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
