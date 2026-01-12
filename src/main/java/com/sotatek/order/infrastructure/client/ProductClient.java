package com.sotatek.order.infrastructure.client;

import com.sotatek.order.infrastructure.client.dto.ProductResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "product-service", url = "${product.service.url}", primary = false)
public interface ProductClient {

    @GetMapping("/products/{id}/stock")
    @CircuitBreaker(name = "productService", fallbackMethod = "checkStockFallback")
    @Retry(name = "productService")
    ProductResponse checkStock(
            @PathVariable("id") Long id,
            @RequestParam("quantity") int requestedQuantity);

    default ProductResponse checkStockFallback(Long id, int requestedQuantity, Throwable throwable) {
        // Fallback: Assume not available to prevent overselling
        return new ProductResponse(id, false, 0, BigDecimal.ZERO);
    }
}
