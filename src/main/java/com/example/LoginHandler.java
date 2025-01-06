package com.example;

import com.google.gson.Gson;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class LoginHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final String USER_POOL_ID = "ap-northeast-2_QQUVB9CQV"; // 替换为实际的用户池ID
    private static final String CLIENT_ID = "i6pb17okhio3ebqqfi9drf9r4"; // 替换为实际的客户端ID
    private static final String CLIENT_SECRET = "1runtectvrc0967adlirt62hp90q393vo04dklg5vpnov3s8mne7"; // 替换为实际的客户端密钥

    private static String sessionToken;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        String path = requestEvent.getPath();
        if ("/login".equals(path)) {
            return handleLoginRequest(requestEvent);
        } else if ("/forgot-password".equals(path)) {
            return handleForgotPasswordRequest(requestEvent);
        } else if ("/set-new-password".equals(path)) {
            return handleSetNewPasswordRequest(requestEvent);
        } else {
            return handleInvalidPathRequest(requestEvent);
        }
    }

    private APIGatewayProxyResponseEvent handleLoginRequest(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
// 解析前端传来的JSON格式的请求体，获取用户名和密码
            Map<String, String> requestBody = new Gson().fromJson(requestEvent.getBody(), HashMap.class);
            String username = requestBody.get("username");
            String password = requestBody.get("password");

// 创建Cognito客户端，使用默认凭证提供程序并指定区域（根据实际情况修改区域）
            CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

// 如果客户端配置了密钥，需要生成SECRET_HASH并添加到验证请求参数中
            Map<String, String> authParameters = new HashMap<>();
            if (CLIENT_SECRET!= null &&!CLIENT_SECRET.isEmpty()) {
                String secretHash = generateSecretHash(username, CLIENT_ID, CLIENT_SECRET);
                authParameters.put("SECRET_HASH", secretHash);
            }
            authParameters.put("USERNAME", username);
            authParameters.put("PASSWORD", password);

// 构造Cognito验证请求
            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .clientId(CLIENT_ID)
                    .authParameters(authParameters)
                    .build();

// 发起验证请求并获取结果
            InitiateAuthResponse authResult = cognitoClient.initiateAuth(authRequest);
            System.out.println(authResult);
            if (authResult.challengeName()!= null && authResult.challengeName() == ChallengeNameType.NEW_PASSWORD_REQUIRED) {
// 如果是 NEW_PASSWORD_REQUIRED 挑战，返回挑战信息给前端，让用户设置新密码（这里可根据实际需求调整返回内容）
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("challengeName", authResult.challengeName().toString());
                responseBody.put("challengeParameters", authResult.challengeParameters());
                //save session
                sessionToken = authResult.session();
                response.setStatusCode(200);
                response.setHeaders(getDefaultHeaders());
                response.setBody(new Gson().toJson(responseBody));
                return response;
            } else if (authResult.authenticationResult()!= null) {
// 如果验证成功，获取访问令牌和刷新令牌并返回给前端
                AuthenticationResultType result = authResult.authenticationResult();
                String idToken = result.idToken();
                String refreshToken = result.refreshToken();
                System.out.println(idToken);
                System.out.println(refreshToken);
// 将令牌添加到返回给前端的响应体中
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("success", true);
                responseBody.put("idToken", idToken);
                responseBody.put("refreshToken", refreshToken);
                response.setStatusCode(200);
                response.setHeaders(getDefaultHeaders());
                response.setBody(new Gson().toJson(responseBody));
                return response;
            } else {
// 其他情况，返回错误信息给前端
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("success", false);
                responseBody.put("errorMessage", "Login failed.");
                response.setStatusCode(401);
                response.setHeaders(getDefaultHeaders());
                response.setBody(new Gson().toJson(responseBody));
                return response;
            }
        } catch (NotAuthorizedException e) {
            e.printStackTrace();
// 捕获验证不通过的特定异常，返回更详细的错误信息给前端（比如密码错误等情况）
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", false);
            responseBody.put("errorMessage", "Invalid username or password.");
            response.setStatusCode(401);
            response.setHeaders(getDefaultHeaders());
            response.setBody(new Gson().toJson(responseBody));
            return response;
        } catch (Exception e) {
            e.printStackTrace();
// 其他异常情况，返回通用的错误信息给前端
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", false);
            responseBody.put("errorMessage", "An error occurred during login.");
            response.setStatusCode(500);
            response.setHeaders(getDefaultHeaders());
            response.setBody(new Gson().toJson(responseBody));
            return response;
        }
    }

    private APIGatewayProxyResponseEvent handleForgotPasswordRequest(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            Map<String, String> requestBody = new Gson().fromJson(requestEvent.getBody(), HashMap.class);
            String username = requestBody.get("username");

// 创建Cognito客户端，使用默认凭证提供程序并指定区域（根据实际情况修改区域）
            CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

// 构造忘记密码请求
            ForgotPasswordRequest forgotPasswordRequest = ForgotPasswordRequest.builder()
                    .clientId(CLIENT_ID)
                    .username(username)
                    .build();

// 发起忘记密码请求并获取响应
            ForgotPasswordResponse forgotPasswordResponse = cognitoClient.forgotPassword(forgotPasswordRequest);

// 根据响应判断是否成功发送确认码，并返回相应结果给前端
            if (forgotPasswordResponse.sdkHttpResponse().isSuccessful()) {
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("success", true);
                responseBody.put("message", "确认码已发送，请查收邮件。");
                response.setStatusCode(200);
                response.setHeaders(getDefaultHeaders());
                response.setBody(new Gson().toJson(responseBody));
                return response;
            } else {
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("success", false);
                responseBody.put("errorMessage", "发送确认码失败。");
                response.setStatusCode(500);
                response.setHeaders(getDefaultHeaders());
                response.setBody(new Gson().toJson(responseBody));
                return response;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", false);
            responseBody.put("errorMessage", "发生错误：" + e.getMessage());
            response.setStatusCode(500);
            response.setHeaders(getDefaultHeaders());
            response.setBody(new Gson().toJson(responseBody));
            return response;
        }
    }

    private APIGatewayProxyResponseEvent handleSetNewPasswordRequest(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            Map<String, String> requestBody = new Gson().fromJson(requestEvent.getBody(), HashMap.class);
            String username = requestBody.get("userId");
            String newPassword = requestBody.get("newPassword");
            System.out.println("handleSetNewPasswordRequest" + "username " + username+"password"+newPassword);

// 创建Cognito客户端，使用默认凭证提供程序并指定区域（根据实际情况修改区域）
            CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

// 构造响应挑战请求
            RespondToAuthChallengeRequest respondToAuthChallengeRequest = RespondToAuthChallengeRequest.builder()
                    .clientId(CLIENT_ID)
                    .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                    .challengeResponses(new HashMap<String, String>() {{
                        put("USERNAME", username);
                        put("NEW_PASSWORD", newPassword);
                        put("SECRET_HASH", generateSecretHash(username, CLIENT_ID, CLIENT_SECRET));
                    }})
                    .session(sessionToken)
                    .build();
// 发起响应挑战请求并获取响应
            RespondToAuthChallengeResponse respondToAuthChallengeResponse = cognitoClient.respondToAuthChallenge(respondToAuthChallengeRequest);
            System.out.println(respondToAuthChallengeResponse);
// 根据响应判断新密码设置是否成功，并返回相应结果给前端
            if (respondToAuthChallengeResponse.sdkHttpResponse().isSuccessful()) {
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("success", true);
                response.setStatusCode(200);
                response.setHeaders(getDefaultHeaders());
                response.setBody(new Gson().toJson(responseBody));
                return response;
            } else {
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("success", false);
                responseBody.put("errorMessage", "Failed to set new password.");
                response.setStatusCode(500);
                response.setHeaders(getDefaultHeaders());
                response.setBody(new Gson().toJson(responseBody));
                return response;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", false);
            responseBody.put("errorMessage", "An error occurred during password reset.");
            response.setStatusCode(500);
            response.setHeaders(getDefaultHeaders());
            response.setBody(new Gson().toJson(responseBody));
            return response;
        }
    }

    private APIGatewayProxyResponseEvent handleInvalidPathRequest(APIGatewayProxyRequestEvent requestEvent) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
// 如果路径不匹配，返回错误信息给前端
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("success", false);
        responseBody.put("errorMessage", "Invalid API path.");
        response.setStatusCode(404);
        response.setHeaders(getDefaultHeaders());
        response.setBody(new Gson().toJson(responseBody));
        return response;
    }

    private Map<String, String> getDefaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return headers;
    }
    private static String generateSecretHash(String userName, String userPoolClientId, String userPoolClientSecret) {
        final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
        SecretKeySpec signingKey = new SecretKeySpec(
                userPoolClientSecret.getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256_ALGORITHM);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(signingKey);
            mac.update(userName.getBytes(StandardCharsets.UTF_8));
            byte[] rawHmac = mac.doFinal(userPoolClientId.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Error while calculating ");
        }
    }

    public static void main(String[] args) {
        // 创建Cognito客户端，使用默认凭证提供程序并指定区域（根据实际情况修改区域）
        CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        Map<String, String> authParameters = new HashMap<>();
        if (CLIENT_SECRET!= null &&!CLIENT_SECRET.isEmpty()) {
            String secretHash = generateSecretHash("test5", CLIENT_ID, CLIENT_SECRET);
            System.out.println(secretHash);
            authParameters.put("SECRET_HASH", secretHash);
        }
        authParameters.put("USERNAME", "test5");
        authParameters.put("PASSWORD", "ASDFqwer.33");

// 构造Cognito验证请求
        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(CLIENT_ID)
                .authParameters(authParameters)
                .build();

        InitiateAuthResponse authResult = cognitoClient.initiateAuth(authRequest);
//        String p = authResult.getValueForField("Session",String.class).get();
//        System.out.println(p);
        System.out.println(authResult);

//        Map<String, String> challengeResponses = new HashMap<>();
//        challengeResponses.put("NEW_PASSWORD","ASDFqwer.33");
//        challengeResponses.put("USERNAME","test5");
//        challengeResponses.put("SECRET_HASH",generateSecretHash("test5", CLIENT_ID, CLIENT_SECRET));
//
//        RespondToAuthChallengeRequest respondToAuthChallengeRequest = RespondToAuthChallengeRequest.builder()
//                .clientId(CLIENT_ID)
//                .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
//                .challengeResponses(challengeResponses)
//                .session(authResult.getValueForField("Session",String.class).get())
//                .build();
//        RespondToAuthChallengeResponse respondToAuthChallengeResponse = cognitoClient.respondToAuthChallenge(respondToAuthChallengeRequest);
//        System.out.println(respondToAuthChallengeResponse);
    }
}