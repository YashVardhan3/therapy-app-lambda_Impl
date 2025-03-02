# Therapy App Backend

This project is a backend for a therapy application implemented using AWS serverless technologies. It uses Java 17 with AWS Lambda, API Gateway, and DynamoDB. The application is built and deployed with AWS SAM CLI, and AWS services are simulated locally using LocalStack and Docker.

## Overview

The Therapy App Backend exposes a collection of REST APIs for:
- User authentication and registration (AuthFunction)
- Managing client and therapist records (ClientFunction)
- Journal creation, retrieval, and management (JournalFunction)
- Messaging between users (MessageFunction)
- Session management (SessionFunction)
- Appointment scheduling (AppointmentFunction)
- Handling therapist and client requests (RequestsFunction)

A Postman collection **Endpoints**(/therapy-app/Therapy%20App%20Endpoints%20Lambda.postman_collection.json) containing all API calls and their saved responses is provided in the `therapy-app` folder.

## Technologies Used

- **Java 17** – The primary programming language for Lambda functions.
- **AWS SAM CLI** – Used for building and deploying the serverless application.
- **LocalStack** – Simulates AWS services (DynamoDB, Lambda, API Gateway) locally.
- **Docker** – Runs LocalStack and related services locally.
- **Postman** – For API testing, with a collection included in the project.

## Setup & Running Locally

1. **Install Docker:**  
   Ensure Docker is installed and running on your system.

2. **Run LocalStack:**  
   Use Docker to start LocalStack:
   ```bash
   docker run --rm -it -p 4566:4566 -p 4571:4571 localstack/localstack
   ```

3. **Install AWS SAM CLI:**  
   Follow the [SAM CLI installation guide](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html).

4. **Build the Application:**  
   In the root directory (`therapy-app`), run:
   ```bash
   sam build
   ```

5. **Start the Local API Gateway:**  
   Run:
   ```bash
   sam local start-api
   ```
   This will start a local API Gateway endpoint (commonly at `http://127.0.0.1:3000`).

6. **Test with Postman:**  
   Import the provided Postman collection from the `therapy-app` folder to test all the APIs.

## Important Notes

- **Authentication Limitations:**  
  The `auth-function/AuthHandler` is implemented for user authentication. However, it will not work properly in the local environment because AWS Cognito (used for authentication) is only available to AWS account holders. For local testing, authentication endpoints might need to be bypassed or dummy tokens supplied.

- **Local Environment:**  
  All AWS services (e.g., DynamoDB, Lambda, API Gateway) are run locally using LocalStack and Docker. Ensure your endpoints are configured to point to the LocalStack endpoint (`http://host.docker.internal:4566`).

- **Postman Collection:**  
  The `therapy-app` folder contains a Postman collection with all API calls and saved responses. This collection can be used for testing the application locally.

## Project Structure

- **auth-function/** – Contains AuthHandler for authentication and RequestsHandler for Therapist and Client requests (note: Cognito is not available locally).
- **client-function/** – Contains ClientHandler for client-related APIs.
- **journal-function/** – Contains JournalHandler for journal operations.
- **message-function/** – Contains MessageHandler for messaging.
- **session-function/** – Contains SessionHandler for session management.
- **appointment-function/** – Contains AppointmentHandler for appointment scheduling.
- **requests-function/** – Contains RequestsHandler for therapist/client requests.
- **therapy-app Postman Collection** – Contains all API calls and saved responses.

## Conclusion

This project demonstrates a locally deployable serverless backend using AWS SAM CLI, LocalStack, and Docker. While most of the APIs work locally, authentication via Cognito is not available for local testing. Use the provided Postman collection to test the endpoints and verify functionality.

---

Feel free to modify this README.md to suit your project needs.
