package com.fooddelivery.config;

import com.fooddelivery.model.MenuItem;
import com.fooddelivery.model.Restaurant;
import com.fooddelivery.repository.MenuItemRepository;
import com.fooddelivery.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository   menuItemRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (restaurantRepository.count() > 0) return;

        List<Restaurant> restaurants = new ArrayList<>();

        // ── 1. Pizza Palace (Italian) ─────────────────────────────────────────
        Restaurant r1 = restaurant("Pizza Palace", "Italian", "123 Main Street, Downtown", 4.5);
        restaurants.add(r1);
        menuItemRepository.saveAll(List.of(
            item("Margherita Pizza",      "Classic tomato base, fresh mozzarella & basil",          12.99, "Pizza",   r1),
            item("Pepperoni Pizza",       "Loaded with spicy pepperoni & melted cheese",             14.99, "Pizza",   r1),
            item("BBQ Chicken Pizza",     "Smoky BBQ sauce, grilled chicken & red onions",           15.99, "Pizza",   r1),
            item("Veggie Supreme Pizza",  "Bell peppers, mushrooms, olives & sun-dried tomatoes",    13.49, "Pizza",   r1),
            item("Four Cheese Pizza",     "Mozzarella, cheddar, parmesan & gorgonzola",              15.49, "Pizza",   r1),
            item("Spaghetti Carbonara",   "Creamy egg sauce, pancetta & parmesan",                   13.99, "Pasta",   r1),
            item("Penne Arrabbiata",      "Spicy tomato sauce with garlic & chilli",                 11.99, "Pasta",   r1),
            item("Garlic Bread",          "Toasted ciabatta with garlic butter & herbs",              4.99, "Sides",   r1),
            item("Caesar Salad",          "Romaine, croutons, parmesan & Caesar dressing",            8.99, "Salads",  r1),
            item("Tiramisu",              "Classic Italian dessert with espresso & mascarpone",       6.99, "Desserts", r1)
        ));

        // ── 2. Burger Barn (American) ─────────────────────────────────────────
        Restaurant r2 = restaurant("Burger Barn", "American", "456 Oak Avenue, Midtown", 4.2);
        restaurants.add(r2);
        menuItemRepository.saveAll(List.of(
            item("Classic Smash Burger",  "Double smash patty, American cheese, pickles & sauce",   10.99, "Burgers", r2),
            item("BBQ Bacon Burger",      "Beef patty, crispy bacon, BBQ sauce & onion rings",       13.99, "Burgers", r2),
            item("Mushroom Swiss Burger", "Beef patty, sautéed mushrooms & Swiss cheese",            12.99, "Burgers", r2),
            item("Spicy Jalapeño Burger", "Beef patty, jalapeños, pepper jack & chipotle mayo",      12.49, "Burgers", r2),
            item("Veggie Black Bean Burger","Black bean patty, avocado, lettuce & tomato",           10.49, "Burgers", r2),
            item("Crispy Chicken Burger", "Fried chicken fillet, coleslaw & honey mustard",          11.99, "Burgers", r2),
            item("Loaded Fries",          "Crispy fries topped with cheese sauce & jalapeños",        5.99, "Sides",   r2),
            item("Onion Rings",           "Beer-battered golden onion rings with dipping sauce",      4.99, "Sides",   r2),
            item("Chocolate Milkshake",   "Thick creamy chocolate milkshake",                         5.49, "Drinks",  r2),
            item("Vanilla Milkshake",     "Classic thick vanilla milkshake",                          5.49, "Drinks",  r2)
        ));

        // ── 3. Sushi Spot (Japanese) ──────────────────────────────────────────
        Restaurant r3 = restaurant("Sushi Spot", "Japanese", "789 Elm Road, East Side", 4.8);
        restaurants.add(r3);
        menuItemRepository.saveAll(List.of(
            item("Salmon Avocado Roll",   "Fresh salmon, avocado & cucumber in nori",               13.99, "Rolls",    r3),
            item("Spicy Tuna Roll",       "Tuna, spicy mayo & cucumber",                            14.49, "Rolls",    r3),
            item("Dragon Roll",           "Shrimp tempura topped with avocado & eel sauce",         16.99, "Rolls",    r3),
            item("Rainbow Roll",          "California roll topped with assorted sashimi",            17.99, "Rolls",    r3),
            item("Salmon Sashimi (6pc)",  "Premium fresh salmon slices",                            15.99, "Sashimi",  r3),
            item("Tuna Sashimi (6pc)",    "Premium bluefin tuna slices",                            17.99, "Sashimi",  r3),
            item("Chicken Teriyaki",      "Grilled chicken with teriyaki glaze & steamed rice",     14.99, "Mains",    r3),
            item("Beef Gyudon",           "Slow-cooked beef & onion over steamed rice",             13.99, "Mains",    r3),
            item("Miso Soup",             "Traditional dashi broth with tofu & wakame",              3.49, "Soups",    r3),
            item("Edamame",               "Steamed salted soybeans",                                 4.49, "Starters", r3)
        ));

        // ── 4. Spice Garden (Indian) ──────────────────────────────────────────
        Restaurant r4 = restaurant("Spice Garden", "Indian", "321 Curry Lane, West End", 4.6);
        restaurants.add(r4);
        menuItemRepository.saveAll(List.of(
            item("Butter Chicken",        "Tender chicken in rich tomato-butter-cream sauce",       14.99, "Mains",    r4),
            item("Lamb Rogan Josh",       "Slow-cooked lamb in aromatic Kashmiri spices",           16.99, "Mains",    r4),
            item("Paneer Tikka Masala",   "Grilled paneer in spiced tomato-cream gravy",            13.99, "Mains",    r4),
            item("Dal Makhani",           "Black lentils slow-cooked with butter & cream",          11.99, "Mains",    r4),
            item("Chicken Biryani",       "Fragrant basmati rice with spiced chicken & saffron",    15.99, "Rice",     r4),
            item("Garlic Naan",           "Soft leavened bread with garlic & butter",                3.49, "Breads",   r4),
            item("Peshwari Naan",         "Sweet naan stuffed with coconut & almonds",               3.99, "Breads",   r4),
            item("Samosa (2pc)",          "Crispy pastry filled with spiced potato & peas",          4.99, "Starters", r4),
            item("Mango Lassi",           "Chilled yogurt drink blended with fresh mango",           4.49, "Drinks",   r4),
            item("Gulab Jamun",           "Soft milk dumplings soaked in rose sugar syrup",          5.49, "Desserts", r4)
        ));

        // ── 5. Taco Fiesta (Mexican) ──────────────────────────────────────────
        Restaurant r5 = restaurant("Taco Fiesta", "Mexican", "654 Salsa Street, South Side", 4.3);
        restaurants.add(r5);
        menuItemRepository.saveAll(List.of(
            item("Carne Asada Tacos (3)", "Grilled beef, onion, cilantro & salsa verde",            12.99, "Tacos",    r5),
            item("Chicken Tacos (3)",     "Grilled chicken, pico de gallo & sour cream",            11.99, "Tacos",    r5),
            item("Fish Tacos (3)",        "Crispy battered fish, cabbage slaw & chipotle",          13.49, "Tacos",    r5),
            item("Beef Burrito",          "Large flour tortilla with beef, rice, beans & cheese",   13.99, "Burritos",  r5),
            item("Veggie Burrito",        "Roasted veggies, black beans, rice & guacamole",         12.49, "Burritos",  r5),
            item("Chicken Quesadilla",    "Grilled chicken & melted cheese in a crispy tortilla",   10.99, "Quesadillas", r5),
            item("Nachos Supreme",        "Tortilla chips, cheese, jalapeños, guac & sour cream",    9.99, "Starters", r5),
            item("Guacamole & Chips",     "Fresh house-made guacamole with tortilla chips",          6.99, "Starters", r5),
            item("Churros",               "Fried dough sticks with cinnamon sugar & chocolate dip",  5.99, "Desserts", r5),
            item("Horchata",              "Sweet rice milk drink with cinnamon",                      3.99, "Drinks",   r5)
        ));

        // ── 6. Noodle House (Chinese) ─────────────────────────────────────────
        Restaurant r6 = restaurant("Noodle House", "Chinese", "987 Dragon Road, Chinatown", 4.4);
        restaurants.add(r6);
        menuItemRepository.saveAll(List.of(
            item("Kung Pao Chicken",      "Diced chicken, peanuts & chillies in savoury sauce",     13.99, "Mains",    r6),
            item("Beef & Broccoli",       "Tender beef strips with broccoli in oyster sauce",       14.49, "Mains",    r6),
            item("Sweet & Sour Pork",     "Crispy pork with pineapple in tangy sauce",              13.49, "Mains",    r6),
            item("Mapo Tofu",             "Silken tofu in spicy Sichuan bean sauce",                11.99, "Mains",    r6),
            item("Char Siu Fried Rice",   "Wok-fried rice with BBQ pork & egg",                    10.99, "Rice",     r6),
            item("Beef Chow Mein",        "Stir-fried noodles with beef & vegetables",              12.99, "Noodles",  r6),
            item("Dan Dan Noodles",       "Spicy Sichuan noodles with minced pork & chilli oil",    11.49, "Noodles",  r6),
            item("Dim Sum Basket (4pc)",  "Assorted steamed dumplings with dipping sauce",           8.99, "Starters", r6),
            item("Spring Rolls (3pc)",    "Crispy vegetable spring rolls with sweet chilli sauce",   5.99, "Starters", r6),
            item("Mango Pudding",         "Silky smooth mango pudding with evaporated milk",         4.99, "Desserts", r6)
        ));

        // ── 7. Green Bowl (Healthy) ───────────────────────────────────────────
        Restaurant r7 = restaurant("Green Bowl", "Healthy", "147 Wellness Way, North Park", 4.7);
        restaurants.add(r7);
        menuItemRepository.saveAll(List.of(
            item("Acai Power Bowl",       "Acai base, granola, banana, berries & honey",            12.99, "Bowls",    r7),
            item("Quinoa Buddha Bowl",    "Quinoa, roasted veggies, chickpeas & tahini dressing",   13.49, "Bowls",    r7),
            item("Grilled Salmon Bowl",   "Grilled salmon, brown rice, edamame & miso dressing",    15.99, "Bowls",    r7),
            item("Chicken & Avocado Wrap","Grilled chicken, avocado, spinach & hummus wrap",        11.99, "Wraps",    r7),
            item("Falafel Wrap",          "Crispy falafel, tabbouleh, hummus & tzatziki",           10.99, "Wraps",    r7),
            item("Greek Salad",           "Tomato, cucumber, olives, feta & oregano dressing",       9.99, "Salads",   r7),
            item("Kale Caesar Salad",     "Kale, parmesan, croutons & light Caesar dressing",       10.49, "Salads",   r7),
            item("Green Detox Smoothie",  "Spinach, cucumber, apple, ginger & lemon",                6.49, "Smoothies", r7),
            item("Berry Blast Smoothie",  "Mixed berries, banana, almond milk & chia seeds",         6.49, "Smoothies", r7),
            item("Avocado Toast",         "Sourdough, smashed avocado, poached egg & chilli flakes", 9.49, "Breakfast", r7)
        ));

        restaurantRepository.saveAll(restaurants);
        log.info("Data initialized: {} restaurants with 10 items each", restaurants.size());
    }

    private Restaurant restaurant(String name, String cuisine, String address, double rating) {
        Restaurant r = new Restaurant();
        r.setName(name);
        r.setCuisine(cuisine);
        r.setAddress(address);
        r.setRating(rating);
        return restaurantRepository.save(r);
    }

    private MenuItem item(String name, String desc, double price, String category, Restaurant r) {
        MenuItem m = new MenuItem();
        m.setName(name);
        m.setDescription(desc);
        m.setPrice(price);
        m.setCategory(category);
        m.setAvailable(true);
        m.setRestaurant(r);
        return m;
    }
}
