package com.sotatek.order.infrastructure.client;

import com.sotatek.order.infrastructure.client.dto.ProductResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dev")
public class MockProductClient implements ProductClient {

    @Override
    public ProductResponse checkStock(Long id, int requestedQuantity) {
        // Mock specific IDs for testing
        if (id.equals(101L)) {
            return new ProductResponse(id, true, 100, new BigDecimal("50.00")); // In Stock
        } else if (id.equals(102L)) {
            return new ProductResponse(id, true, 2, new BigDecimal("100.00")); // Low Stock
        } else if (id.equals(103L)) {
            return new ProductResponse(id, true, 0, new BigDecimal("25.00")); // Out of Stock
        }
        return new ProductResponse(id, false, 0, BigDecimal.ZERO); // Not Available
    }
}
