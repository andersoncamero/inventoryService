package com.inventoryservice.inventoryservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;


@Entity
@DiscriminatorValue("COLLECTION")
public class CollectionOperationalEvent extends OperationalEvent {
    @Column(name = "supplier_id")
    private long supplierId;

    @Column(name = "collected_quantity", precision = 14, scale = 3)
    private BigDecimal collectedQuantity;

    public long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(long supplierId) {
        this.supplierId = supplierId;
    }

    public BigDecimal getCollectedQuantity() {
        return collectedQuantity;
    }

    public void setCollectedQuantity(BigDecimal collectedQuantity) {
        this.collectedQuantity = collectedQuantity;
    }

    public CollectionOperationalEvent() {}
}
