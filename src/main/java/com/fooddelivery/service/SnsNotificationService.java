package com.fooddelivery.service;

import com.fooddelivery.model.Order;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SnsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SnsNotificationService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SnsClient snsClient;

    @Value("${aws.sns.topic.arn:}")
    private String topicArn;

    public void publishOrderStatusUpdate(Order order) {
        if (topicArn == null || topicArn.isBlank()) {
            log.warn("SNS_TOPIC_ARN not set — skipping notification for Order #{}", order.getId());
            return;
        }
        String subject = buildSubject(order.getStatus());
        String message = buildMessage(order);

        try {
            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(subject)
                    .message(message)
                    .build());
            log.info("SNS notification sent for Order #{} - Status: {}", order.getId(), order.getStatus());
        } catch (Exception e) {
            log.error("Failed to send SNS notification for Order #{}: {}", order.getId(), e.getMessage());
        }
    }

    private String buildSubject(Order.OrderStatus status) {
        return switch (status) {
            case ORDER_RECEIVED  -> "🍽️ Order Received!";
            case PREPARING       -> "👨‍🍳 Your Order is Being Prepared";
            case OUT_FOR_DELIVERY -> "🚴 Your Order is Out for Delivery";
            case DELIVERED       -> "✅ Order Delivered!";
        };
    }

    private String buildMessage(Order order) {
        return String.format("""
                Food Delivery - Order Status Update
                =====================================
                Order ID    : #%d
                Status      : %s
                Customer    : %s (%s)
                Restaurant  : %s
                Total Amount: $%.2f
                Updated At  : %s
                =====================================
                %s
                """,
                order.getId(),
                order.getStatus(),
                order.getUser().getName(),
                order.getUser().getEmail(),
                order.getRestaurant().getName(),
                order.getTotalAmount(),
                order.getUpdatedAt().format(FORMATTER),
                getStatusMessage(order.getStatus())
        );
    }

    private String getStatusMessage(Order.OrderStatus status) {
        return switch (status) {
            case ORDER_RECEIVED  -> "We have received your order and will start preparing it shortly.";
            case PREPARING       -> "Our chefs are preparing your delicious meal!";
            case OUT_FOR_DELIVERY -> "Your order is on its way. Estimated delivery: 30 minutes.";
            case DELIVERED       -> "Your order has been delivered. Enjoy your meal!";
        };
    }
}
