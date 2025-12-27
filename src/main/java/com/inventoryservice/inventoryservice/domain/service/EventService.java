package com.inventoryservice.inventoryservice.domain.service;


import com.inventoryservice.inventoryservice.domain.dtos.CreateEventRequest;
import com.inventoryservice.inventoryservice.exception.InvalidLicenseException;
import com.inventoryservice.inventoryservice.exception.InvalidWarehouseException;
import com.inventoryservice.inventoryservice.exception.UnauthorizedException;
import com.inventoryservice.inventoryservice.persistence.entity.*;
import com.inventoryservice.inventoryservice.repository.EventRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class EventService {
    private final EventRepository repository;
    private final LicenseService licenseService;

    public EventService(EventRepository repository, LicenseService licenseService){
        this.repository = repository;
        this.licenseService = licenseService;
    }

    @Transactional
    public void createEvent(CreateEventRequest r) {
        // Validar licencia antes de crear el evento
        validateLicense(r.getLicenseId(), r.getCreatedBy(), r.getWarehouseId());

        OperationalEvent event = switch (r.getEventType()){
            case COLLECTION -> {
                CollectionOperationalEvent e = new CollectionOperationalEvent();
                e.setSupplierId(r.getSupplierId());
                e.setCollectedQuantity(r.getCollectedQuantity());
                e.setCreatedBy(r.getCreatedBy());
                yield e;

            }
            case TRANSFER -> {
                TransferOperationalEvent e = new TransferOperationalEvent();
                e.setSourceWareHouseId(r.getSourceWarehouseId());
                e.setTargetWarehouseId(r.getTargetWarehouseId());
                e.setTransferredQuantity(r.getTransferredQuantity());
                e.setCreatedBy(r.getCreatedBy());

                yield e;

            }
            case SALE -> {
                SaleOperationalEvent e = new SaleOperationalEvent();
                e.setCustomerId(r.getCustomerId());
                e.setSoldQuantity(r.getSoldQuantity());
                e.setUnitPrice(r.getUnitPrice());
                e.setCreatedBy(r.getCreatedBy());

                yield e;
            }
            case ADJUSTMENT -> {
                AdjustmentOperationalEvent e = new AdjustmentOperationalEvent();
                e.setReason(r.getReason());
                e.setAdjustedQuantity(r.getAdjustedQuantity());
                e.setCreatedBy(r.getCreatedBy());
                yield e;
            }
        };

        // Asignar campos comunes a todos los eventos
        event.setLicenseId(r.getLicenseId());
        event.setWarehouseIdl(r.getWarehouseId());
        event.setItemId(r.getItemId());

        repository.save(event);
    }


    private void validateLicense(Long licenseId, Long createdBy, Long warehouseId) {
        if (!licenseService.isLicenseValid(licenseId)) {
            throw new InvalidLicenseException("La licencia no es válida o no está activa");
        }
        if (!licenseService.hasUserAccess(licenseId, createdBy)) {
            throw new UnauthorizedException("El usuario no tiene acceso a esta licencia");
        }
        if (!licenseService.isWarehouseInLicense(licenseId, warehouseId)) {
            throw new InvalidWarehouseException("El almacén no pertenece a esta licencia");
        }
    }

}
