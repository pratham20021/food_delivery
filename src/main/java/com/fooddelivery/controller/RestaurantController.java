package com.fooddelivery.controller;

import com.fooddelivery.dto.Dtos.ApiResponse;
import com.fooddelivery.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @GetMapping
    public ResponseEntity<ApiResponse> getAllRestaurants() {
        return ResponseEntity.ok(new ApiResponse(true, "Success", restaurantService.getAllRestaurants()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getRestaurant(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse(true, "Success", restaurantService.getRestaurantById(id)));
    }

    @GetMapping("/{id}/menu")
    public ResponseEntity<ApiResponse> getMenu(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse(true, "Success", restaurantService.getMenuByRestaurant(id)));
    }
}
