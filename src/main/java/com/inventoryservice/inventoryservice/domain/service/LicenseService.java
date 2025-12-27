package com.inventoryservice.inventoryservice.domain.service;

import org.springframework.stereotype.Service;
@Service
public class LicenseService {
    public boolean isLicenseValid(long licenseId) {
        if (licenseId <= 0) {
            return false;
        }
        return true;
    }
    public boolean hasUserAccess(long licenseId, long userId) {
        if (userId <= 0) {
            return false;
        }

        return true;
    }

    public boolean isWarehouseInLicense(long licenseId, long warehouseId) {
        if (warehouseId <= 0) {
            return false;
        }
        return true;
    }
}

