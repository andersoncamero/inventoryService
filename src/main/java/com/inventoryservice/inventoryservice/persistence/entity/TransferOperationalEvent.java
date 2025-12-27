package com.inventoryservice.inventoryservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;


import java.math.BigDecimal;


@Entity
@DiscriminatorValue("TRANSFER")
public class TransferOperationalEvent extends OperationalEvent {
    @Column(name = "source_warehouse_id")
    private long sourceWareHouseId;

    @Column(name= "target_warehouse_id")
    private long targetWarehouseId;

    @Column(name = "transferred_quantity", precision = 14, scale = 3)
    private BigDecimal transferredQuantity;

    public long getSourceWareHouseId() {
        return sourceWareHouseId;
    }

    public void setSourceWareHouseId(long sourceWareHouseId) {
        this.sourceWareHouseId = sourceWareHouseId;
    }

    public long getTargetWarehouseId() {
        return targetWarehouseId;
    }

    public void setTargetWarehouseId(long targetWarehouseId) {
        this.targetWarehouseId = targetWarehouseId;
    }

    public BigDecimal getTransferredQuantity() {
        return transferredQuantity;
    }

    public void setTransferredQuantity(BigDecimal transferredQuantity) {
        this.transferredQuantity = transferredQuantity;
    }

    public TransferOperationalEvent() {}
}
