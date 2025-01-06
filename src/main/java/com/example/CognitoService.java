package com.example;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
@Service
public class CognitoService {
    private static final String USER_POOL_ID = "ap-northeast-2_QQUVB9CQV"; // 替换为实际的用户池ID
    private static final String CLIENT_ID = "i6pb17okhio3ebqqfi9drf9r4"; // 替换为实际的客户端ID
    private static final String CLIENT_SECRET = "1runtectvrc0967adlirt62hp90q393vo04dklg5vpnov3s8mne7"; // 替换为实际的客户端密钥

    private CognitoIdentityProviderClient cognitoClient;

    public CognitoService() {
        cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public Map<String, Object> login(String username, String password) {
        Map<String, String> authParameters = new HashMap<>();
        if (CLIENT_SECRET!= null &&!CLIENT_SECRET.isEmpty()) {
            String secretHash = generateSecretHash(username, CLIENT_ID, CLIENT_SECRET);
            authParameters.put("SECRET_HASH", secretHash);
        }
        authParameters.put("USERNAME", username);
        authParameters.put("PASSWORD", password);

        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(CLIENT_ID)
                .authParameters(authParameters)
                .build();

        InitiateAuthResponse authResult = cognitoClient.initiateAuth(authRequest);
        if (authResult.challengeName()!= null && authResult.challengeName() == ChallengeNameType.NEW_PASSWORD_REQUIRED) {
            // 如果是 NEW_PASSWORD_REQUIRED 挑战，返回挑战信息给前端，让用户设置新密码（这里可根据实际需求调整返回内容）
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("challengeName", authResult.challengeName().toString());
            responseBody.put("challengeParameters", authResult.challengeParameters());
            return responseBody;
        } else if (authResult.authenticationResult()!= null) {
            // 如果验证成功，获取访问令牌和刷新令牌并返回给前端
            AuthenticationResultType result = authResult.authenticationResult();
            String idToken = result.idToken();
            String refreshToken = result.refreshToken();
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("idToken", idToken);
            responseBody.put("refreshToken", refreshToken);
            return responseBody;
        } else {
            // 其他情况，返回错误信息给前端
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", false);
            responseBody.put("errorMessage", "Login failed.");
            return responseBody;
        }
    }

    public Map<String, Object> forgotPassword(String username) {
        ForgotPasswordRequest forgotPasswordRequest = ForgotPasswordRequest.builder()
                .clientId(CLIENT_ID)
                .username(username)
                .build();

        ForgotPasswordResponse forgotPasswordResponse = cognitoClient.forgotPassword(forgotPasswordRequest);

        // 根据响应判断是否成功发送确认码，并返回相应结果给前端
        Map<String, Object> responseBody = new HashMap<>();
        if (forgotPasswordResponse.sdkHttpResponse().isSuccessful()) {
            responseBody.put("success", true);
            responseBody.put("message", "确认码已发送，请查收邮件。");
        } else {
            responseBody.put("success", false);
            responseBody.put("errorMessage", "发送确认码失败。");
        }
        return responseBody;
    }

    public Map<String, Object> setNewPassword(String username, String newPassword, String sessionToken) {
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

        RespondToAuthChallengeResponse respondToAuthChallengeResponse = cognitoClient.respondToAuthChallenge(respondToAuthChallengeRequest);

        // 根据响应判断新密码设置是否成功，并返回相应结果给前端
        Map<String, Object> responseBody = new HashMap<>();
        if (respondToAuthChallengeResponse.sdkHttpResponse().isSuccessful()) {
            responseBody.put("success", true);
        } else {
            responseBody.put("success", false);
            responseBody.put("errorMessage", "Failed to set new password.");
        }
        return responseBody;
    }

    private String generateSecretHash(String userName, String userPoolClientId, String userPoolClientSecret) {
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
}