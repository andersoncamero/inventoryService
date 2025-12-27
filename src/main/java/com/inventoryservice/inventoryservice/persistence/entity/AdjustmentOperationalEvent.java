package com.inventoryservice.inventoryservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;


import java.math.BigDecimal;

@Entity
@DiscriminatorValue("ADJUSTMENT")
public class AdjustmentOperationalEvent extends OperationalEvent {
    @Column()
    private String reason;

    @Column(name = "adjusted_quantity", precision = 14, scale = 3)
    private BigDecimal adjustedQuantity;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public BigDecimal getAdjustedQuantity() {
        return adjustedQuantity;
    }

    public void setAdjustedQuantity(BigDecimal adjustedQuantity) {
        this.adjustedQuantity = adjustedQuantity;
    }

    public AdjustmentOperationalEvent() {}
}
