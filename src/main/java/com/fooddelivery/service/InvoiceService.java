package com.fooddelivery.service;

import com.fooddelivery.model.Order;
import com.fooddelivery.model.OrderItem;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SesClient sesClient;

    @Value("${aws.ses.from.email:}")
    private String fromEmail;

    /** NEVER throws — all exceptions caught internally */
    public void sendInvoiceEmail(Order order) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("SES_FROM_EMAIL not set, skipping invoice Order #{}", order.getId());
            return;
        }
        try {
            String invoice = buildInvoice(order);
            sesClient.sendEmail(SendEmailRequest.builder()
                    .source(fromEmail)
                    .destination(Destination.builder()
                            .toAddresses(order.getUser().getEmail()).build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data("Your Invoice - Food Delivery Order #" + order.getId()).build())
                            .body(Body.builder()
                                    .text(Content.builder().data(invoice).build())
                                    .build())
                            .build())
                    .build());
            log.info("Invoice email sent to {} for Order #{}", order.getUser().getEmail(), order.getId());
        } catch (Exception e) {
            log.error("Invoice email failed Order #{}: {}", order.getId(), e.getMessage());
        }
    }

    private String buildInvoice(Order order) {
        StringBuilder items = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            items.append(String.format("  %-30s x%d   $%.2f%n",
                    item.getMenuItem().getName(),
                    item.getQuantity(),
                    item.getPrice() * item.getQuantity()));
        }
        return "========================================\n"
             + "       FOOD DELIVERY - INVOICE\n"
             + "========================================\n"
             + "Invoice Date  : " + order.getUpdatedAt().format(FMT) + "\n"
             + "Order ID      : #" + order.getId() + "\n"
             + "----------------------------------------\n"
             + "Customer      : " + order.getUser().getName() + "\n"
             + "Email         : " + order.getUser().getEmail() + "\n"
             + "Restaurant    : " + order.getRestaurant().getName() + "\n"
             + "Delivery Addr : " + order.getDeliveryAddress() + "\n"
             + "----------------------------------------\n"
             + "Items:\n"
             + items
             + "----------------------------------------\n"
             + "Total Amount  : $" + String.format("%.2f", order.getTotalAmount()) + "\n"
             + "Status        : DELIVERED\n"
             + "========================================\n"
             + "Thank you for ordering with Food Delivery!";
    }
}
