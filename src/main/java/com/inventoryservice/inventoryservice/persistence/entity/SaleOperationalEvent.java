package com.inventoryservice.inventoryservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;


import java.math.BigDecimal;


@Entity
@DiscriminatorValue("SALE")
public class SaleOperationalEvent extends OperationalEvent {
    @Column(name = "customer_id")
    private long customerId;

    @Column(name = "sold_quantity", precision = 14, scale = 3)
    private BigDecimal soldQuantity;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    public long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(long customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getSoldQuantity() {
        return soldQuantity;
    }

    public void setSoldQuantity(BigDecimal soldQuantity) {
        this.soldQuantity = soldQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public SaleOperationalEvent() {}
}
