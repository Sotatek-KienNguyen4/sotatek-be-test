package com.sotatek.order.dto;

import com.sotatek.order.entity.OrderStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequestDTO {
    @NotNull
    private Long memberId;

    @NotNull
    private Long productId;

    @Min(1)
    private Integer quantity;

    @NotNull
    @Min(0)
    private BigDecimal totalPrice;

    private OrderStatus status;
}
