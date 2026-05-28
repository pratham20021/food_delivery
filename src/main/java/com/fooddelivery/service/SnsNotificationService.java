package com.fooddelivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.model.Order;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SnsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SnsNotificationService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SnsClient    snsClient;
    private final SesClient    sesClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.topic.arn:}")
    private String topicArn;

    @Value("${aws.ses.from.email:}")
    private String fromEmail;

    /** Called by scheduler and order service — NEVER throws */
    public void publishOrderStatusUpdate(Order order) {
        publishToSns(order);
        sendSesEmail(order);
    }

    // ── SNS ──────────────────────────────────────────────────────────────────
    private void publishToSns(Order order) {
        if (topicArn == null || topicArn.isBlank()) return;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId",         order.getId());
            payload.put("status",          order.getStatus().name());
            payload.put("customerName",    order.getUser().getName());
            payload.put("customerEmail",   order.getUser().getEmail());
            payload.put("restaurantName",  order.getRestaurant().getName());
            payload.put("totalAmount",     order.getTotalAmount());
            payload.put("deliveryAddress", order.getDeliveryAddress());
            payload.put("updatedAt",       order.getUpdatedAt().format(FMT));

            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject("ORDER_EVENT:" + order.getStatus().name())
                    .message(objectMapper.writeValueAsString(payload))
                    .build());
            log.info("SNS published Order #{} -> {}", order.getId(), order.getStatus());
        } catch (Exception e) {
            log.error("SNS failed Order #{}: {}", order.getId(), e.getMessage());
        }
    }

    // ── SES direct email ─────────────────────────────────────────────────────
    private void sendSesEmail(Order order) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("SES_FROM_EMAIL not set, skipping email Order #{}", order.getId());
            return;
        }
        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .source(fromEmail)
                    .destination(Destination.builder()
                            .toAddresses(order.getUser().getEmail()).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(emailSubject(order.getStatus())).build())
                            .body(Body.builder()
                                    .text(Content.builder().data(emailBody(order)).build())
                                    .build())
                            .build())
                    .build());
            log.info("SES email sent to {} Order #{} -> {}",
                    order.getUser().getEmail(), order.getId(), order.getStatus());
        } catch (Exception e) {
            log.error("SES failed Order #{}: {}", order.getId(), e.getMessage());
        }
    }

    private String emailSubject(Order.OrderStatus s) {
        return switch (s) {
            case ORDER_RECEIVED   -> "Order Received - Food Delivery";
            case PREPARING        -> "Your Order is Being Prepared - Food Delivery";
            case OUT_FOR_DELIVERY -> "Your Order is Out for Delivery - Food Delivery";
            case DELIVERED        -> "Order Delivered - Enjoy your meal!";
        };
    }

    private String emailBody(Order order) {
        String msg = switch (order.getStatus()) {
            case ORDER_RECEIVED   -> "We have received your order and will start preparing it shortly.";
            case PREPARING        -> "Our chefs are preparing your delicious meal!";
            case OUT_FOR_DELIVERY -> "Your order is on its way. Estimated delivery: 30 minutes.";
            case DELIVERED        -> "Your order has been delivered. Enjoy your meal!";
        };
        return "Food Delivery - Order Status Update\n"
             + "=====================================\n"
             + "Order ID     : #" + order.getId() + "\n"
             + "Status       : " + order.getStatus().name() + "\n"
             + "Customer     : " + order.getUser().getName() + "\n"
             + "Restaurant   : " + order.getRestaurant().getName() + "\n"
             + "Total Amount : $" + String.format("%.2f", order.getTotalAmount()) + "\n"
             + "Delivery Addr: " + order.getDeliveryAddress() + "\n"
             + "Updated At   : " + order.getUpdatedAt().format(FMT) + "\n"
             + "=====================================\n"
             + msg;
    }
}
