package com.sotatek.order.infrastructure.client;

import com.sotatek.order.infrastructure.client.dto.PaymentRequest;
import com.sotatek.order.infrastructure.client.dto.PaymentResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dev")
public class MockPaymentClient implements PaymentClient {

    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        // Mock logic: Amount > 1000 fails
        if (request.getAmount().compareTo(BigDecimal.valueOf(1000)) > 0) {
            return new PaymentResponse(null, false, "Insufficient funds or limit exceeded");
        }
        return new PaymentResponse(UUID.randomUUID().toString(), true, "Payment successful");
    }
}
