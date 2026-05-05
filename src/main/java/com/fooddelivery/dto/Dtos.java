package com.fooddelivery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

public class Dtos {

    @Data
    public static class RegisterRequest {
        @NotBlank private String name;
        @Email @NotBlank private String email;
        @NotBlank private String password;
        private String phone;
    }

    @Data
    public static class LoginRequest {
        @Email @NotBlank private String email;
        @NotBlank private String password;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private String email;
        private String name;
        private String role;

        public AuthResponse(String token, String email, String name, String role) {
            this.token = token;
            this.email = email;
            this.name = name;
            this.role = role;
        }
    }

    @Data
    public static class OrderRequest {
        @NotNull private Long restaurantId;
        @NotNull private List<OrderItemRequest> items;
        @NotBlank private String deliveryAddress;
    }

    @Data
    public static class OrderItemRequest {
        @NotNull private Long menuItemId;
        @NotNull private Integer quantity;
    }

    @Data
    public static class ApiResponse {
        private boolean success;
        private String message;
        private Object data;

        public ApiResponse(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }
}
