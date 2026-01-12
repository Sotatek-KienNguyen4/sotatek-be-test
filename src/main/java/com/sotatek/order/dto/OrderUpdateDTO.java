package com.sotatek.order.dto;

import com.sotatek.order.entity.OrderStatus;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderUpdateDTO {
    private Long memberId;          // optional
    private Long productId;         // optional
    private @Min(1) Integer quantity;   // optional
    private @Min(0) BigDecimal totalPrice; // optional
    private OrderStatus status;     // optional, dùng để cancel
}