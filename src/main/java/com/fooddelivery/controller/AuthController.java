package com.fooddelivery.controller;

import com.fooddelivery.dto.Dtos.*;
import com.fooddelivery.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterRequest req) {
        AuthResponse auth = authService.register(req);
        return ResponseEntity.ok(new ApiResponse(true, "Registration successful", auth));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse auth = authService.login(req);
        return ResponseEntity.ok(new ApiResponse(true, "Login successful", auth));
    }
}
