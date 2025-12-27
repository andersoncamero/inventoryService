package com.inventoryservice.inventoryservice.domain.dtos;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class CreateEventRequest {

    @NotNull(message = "El tipo de evento es requerido")
    private EventType eventType;

    @NotNull(message = "El ID de la licencia es requerido")
    @Min(value = 1, message = "El ID de la licencia debe ser mayor a 0")
    private Long licenseId;

    @NotNull(message = "El ID del almacén es requerido")
    @Min(value = 1, message = "El ID del almacén debe ser mayor a 0")
    private Long warehouseId;

    @NotNull(message = "El ID del artículo es requerido")
    @Min(value = 1, message = "El ID del artículo debe ser mayor a 0")
    private Long itemId;

    @NotNull(message = "El ID del usuario creador es requerido")
    @Min(value = 1, message = "El ID del usuario creador debe ser mayor a 0")
    private Long createdBy;

  
    private Long supplierId;
    private BigDecimal collectedQuantity;

    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private BigDecimal transferredQuantity;


    private Long customerId;
    private BigDecimal soldQuantity;
    private BigDecimal unitPrice;


    private String reason;
    private BigDecimal adjustedQuantity;

    @AssertTrue(message = "Para eventos COLLECTION, supplierId y collectedQuantity son requeridos")
    public boolean isValidCollectionEvent() {
        if (eventType == null) return true;
        if (eventType != EventType.COLLECTION) return true;
        return supplierId != null && supplierId > 0 
            && collectedQuantity != null 
            && collectedQuantity.compareTo(BigDecimal.ZERO) > 0;
    }

    @AssertTrue(message = "Para eventos TRANSFER, sourceWarehouseId, targetWarehouseId y transferredQuantity son requeridos")
    public boolean isValidTransferEvent() {
        if (eventType == null) return true; 
        if (eventType != EventType.TRANSFER) return true;
        return sourceWarehouseId != null && sourceWarehouseId > 0
            && targetWarehouseId != null && targetWarehouseId > 0
            && sourceWarehouseId != targetWarehouseId
            && transferredQuantity != null 
            && transferredQuantity.compareTo(BigDecimal.ZERO) > 0;
    }

    @AssertTrue(message = "Para eventos SALE, customerId, soldQuantity y unitPrice son requeridos")
    public boolean isValidSaleEvent() {
        if (eventType == null) return true;
        if (eventType != EventType.SALE) return true;
        return customerId != null && customerId > 0
            && soldQuantity != null 
            && soldQuantity.compareTo(BigDecimal.ZERO) > 0
            && unitPrice != null 
            && unitPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    @AssertTrue(message = "Para eventos ADJUSTMENT, reason y adjustedQuantity son requeridos. La razón no puede exceder 255 caracteres")
    public boolean isValidAdjustmentEvent() {
        if (eventType == null) return true;
        if (eventType != EventType.ADJUSTMENT) return true;
        return reason != null && !reason.trim().isEmpty() && reason.length() <= 255
            && adjustedQuantity != null 
            && adjustedQuantity.abs().compareTo(BigDecimal.ZERO) > 0;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Long getLicenseId() {
        return licenseId;
    }

    public void setLicenseId(Long licenseId) {
        this.licenseId = licenseId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public BigDecimal getCollectedQuantity() {
        return collectedQuantity;
    }

    public void setCollectedQuantity(BigDecimal collectedQuantity) {
        this.collectedQuantity = collectedQuantity;
    }

    public Long getSourceWarehouseId() {
        return sourceWarehouseId;
    }

    public void setSourceWarehouseId(Long sourceWarehouseId) {
        this.sourceWarehouseId = sourceWarehouseId;
    }

    public Long getTargetWarehouseId() {
        return targetWarehouseId;
    }

    public void setTargetWarehouseId(Long targetWarehouseId) {
        this.targetWarehouseId = targetWarehouseId;
    }

    public BigDecimal getTransferredQuantity() {
        return transferredQuantity;
    }

    public void setTransferredQuantity(BigDecimal transferredQuantity) {
        this.transferredQuantity = transferredQuantity;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
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

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
