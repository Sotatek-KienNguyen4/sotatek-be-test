package com.sotatek.order.infrastructure.client;

import com.sotatek.order.infrastructure.client.dto.PaymentRequest;
import com.sotatek.order.infrastructure.client.dto.PaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", url = "${payment.service.url}")
public interface PaymentClient {

    @PostMapping("/payments")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    @Retry(name = "paymentService")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);

    default PaymentResponse processPaymentFallback(PaymentRequest request, Throwable throwable) {
        return new PaymentResponse(null, false, "Payment service unavailable or failed: " + throwable.getMessage());
    }
}
