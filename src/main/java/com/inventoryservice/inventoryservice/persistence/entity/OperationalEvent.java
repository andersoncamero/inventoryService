package com.inventoryservice.inventoryservice.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;


@Entity
@Table(name="events")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
        name = "event_type",
        discriminatorType = DiscriminatorType.STRING
)
public abstract class OperationalEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

   @Column(name = "item_id", nullable = false)
   private long itemId;

    @Column(name="license_id", nullable = false)
    private long licenseId;

    @Column(name = "warehouse_id", nullable = false)
    private long warehouseIdl;

    @Column(name= "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name="created_by", nullable = false)
    private long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public long getWarehouseIdl() {
        return warehouseIdl;
    }

    public void setWarehouseIdl(long warehouseIdl) {
        this.warehouseIdl = warehouseIdl;
    }

    public long getLicenseId() {
        return licenseId;
    }

    public void setLicenseId(long licenseId) {
        this.licenseId = licenseId;
    }

    public long getItemId() {
        return itemId;
    }

    public void setItemId(long itemId) {
        this.itemId = itemId;
    }

    public Instant getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Instant createAt) {
        this.createAt = createAt;
    }

    public long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(long createdBy) {
        this.createdBy = createdBy;
    }

    protected OperationalEvent() {
        this.createAt = Instant.now();
        this.eventTimestamp = Instant.now();
    }
}
