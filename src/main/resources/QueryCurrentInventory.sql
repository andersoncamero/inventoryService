SELECT
    license_id,
    warehouse_id,
    item_id,
    SUM(
        CASE event_type
            WHEN 'COLLECTION' THEN collected_quantity
            WHEN 'SALE' THEN -sold_quantity
            WHEN 'ADJUSTMENT' THEN adjusted_quantity
            WHEN 'TRANSFER' THEN
                CASE
                    WHEN warehouse_id = source_warehouse_id THEN -transferred_quantity
                    WHEN warehouse_id = target_warehouse_id THEN transferred_quantity
                    ELSE 0
                END
            ELSE 0
        END
    ) AS current_inventory
FROM events
GROUP BY license_id, warehouse_id, item_id;