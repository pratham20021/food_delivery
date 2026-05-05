package com.fooddelivery.config;

import com.fooddelivery.model.MenuItem;
import com.fooddelivery.model.Restaurant;
import com.fooddelivery.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final RestaurantRepository restaurantRepository;

    @Override
    public void run(String... args) {
        if (restaurantRepository.count() > 0) return;

        Restaurant r1 = createRestaurant("Pizza Palace", "Italian", "123 Main St", 4.5);
        r1.setMenuItems(List.of(
                menuItem("Margherita Pizza", "Classic tomato & mozzarella", 12.99, "Pizza", r1),
                menuItem("Pepperoni Pizza", "Loaded with pepperoni", 14.99, "Pizza", r1),
                menuItem("Garlic Bread", "Toasted with garlic butter", 4.99, "Sides", r1)
        ));

        Restaurant r2 = createRestaurant("Burger Barn", "American", "456 Oak Ave", 4.2);
        r2.setMenuItems(List.of(
                menuItem("Classic Burger", "Beef patty with lettuce & tomato", 9.99, "Burgers", r2),
                menuItem("Cheese Burger", "Double cheese beef burger", 11.99, "Burgers", r2),
                menuItem("Fries", "Crispy golden fries", 3.99, "Sides", r2)
        ));

        Restaurant r3 = createRestaurant("Sushi Spot", "Japanese", "789 Elm Rd", 4.8);
        r3.setMenuItems(List.of(
                menuItem("Salmon Roll", "Fresh salmon with avocado", 13.99, "Rolls", r3),
                menuItem("Tuna Sashimi", "Premium tuna slices", 16.99, "Sashimi", r3),
                menuItem("Miso Soup", "Traditional Japanese soup", 3.49, "Soups", r3)
        ));

        restaurantRepository.saveAll(List.of(r1, r2, r3));
        log.info("Sample data initialized: 3 restaurants loaded");
    }

    private Restaurant createRestaurant(String name, String cuisine, String address, double rating) {
        Restaurant r = new Restaurant();
        r.setName(name);
        r.setCuisine(cuisine);
        r.setAddress(address);
        r.setRating(rating);
        return r;
    }

    private MenuItem menuItem(String name, String desc, double price, String category, Restaurant r) {
        MenuItem m = new MenuItem();
        m.setName(name);
        m.setDescription(desc);
        m.setPrice(price);
        m.setCategory(category);
        m.setRestaurant(r);
        return m;
    }
}
