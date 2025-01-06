package com.example;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoginController {
    @Autowired
    private CognitoService cognitoService;

    @PostMapping("/login")
    public String handleLoginRequest(@RequestBody Map<String, String> requestBody) {
        String username = requestBody.get("username");
        String password = requestBody.get("password");
        Map<String, Object> response = cognitoService.login(username, password);
        return new Gson().toJson(response);
    }

    @PostMapping("/forgot-password")
    public String handleForgotPasswordRequest(@RequestBody Map<String, String> requestBody) {
        String username = requestBody.get("username");
        Map<String, Object> response = cognitoService.forgotPassword(username);
        return new Gson().toJson(response);
    }

    @PostMapping("/set-new-password")
    public String handleSetNewPasswordRequest(@RequestBody Map<String, String> requestBody) {
        String username = requestBody.get("userId");
        String newPassword = requestBody.get("newPassword");
        String sessionToken = requestBody.get("sessionToken");
        Map<String, Object> response = cognitoService.setNewPassword(username, newPassword, sessionToken);
        return new Gson().toJson(response);
    }
}