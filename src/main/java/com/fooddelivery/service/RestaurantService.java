package com.fooddelivery.service;

import com.fooddelivery.exception.ResourceNotFoundException;
import com.fooddelivery.model.MenuItem;
import com.fooddelivery.model.Restaurant;
import com.fooddelivery.repository.MenuItemRepository;
import com.fooddelivery.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;

    public List<Restaurant> getAllRestaurants() {
        return restaurantRepository.findAll();
    }

    public Restaurant getRestaurantById(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + id));
    }

    public List<MenuItem> getMenuByRestaurant(Long restaurantId) {
        getRestaurantById(restaurantId); // validate exists
        return menuItemRepository.findByRestaurantIdAndAvailableTrue(restaurantId);
    }
}
