package com.fooddelivery.controller;

import com.fooddelivery.dto.Dtos.*;
import com.fooddelivery.model.Order;
import com.fooddelivery.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse> placeOrder(@AuthenticationPrincipal UserDetails user,
                                                   @Valid @RequestBody OrderRequest req) {
        Order order = orderService.placeOrder(user.getUsername(), req);
        return ResponseEntity.ok(new ApiResponse(true, "Order placed successfully", order));
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getMyOrders(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(new ApiResponse(true, "Success", orderService.getUserOrders(user.getUsername())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse(true, "Success", orderService.getOrderById(id)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse> updateStatus(@PathVariable Long id,
                                                     @RequestParam Order.OrderStatus status) {
        Order updated = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(new ApiResponse(true, "Status updated", updated));
    }
}
