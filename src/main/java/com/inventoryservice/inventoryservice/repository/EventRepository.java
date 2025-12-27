package com.inventoryservice.inventoryservice.repository;

import com.inventoryservice.inventoryservice.persistence.entity.OperationalEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<OperationalEvent, Long> {
}
