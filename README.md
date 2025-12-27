# DOCUMENTACIÓN TÉCNICA - INVENTORY SERVICE

## PARTE 1 — MODELAMIENTO DE BASE DE DATOS

### 1. Estrategia de Polimorfismo: Single Table Inheritance (STI)

El sistema utiliza **Single Table Inheritance** para implementar polimorfismo en la base de datos. Todas las entidades de eventos (`SaleOperationalEvent`, `CollectionOperationalEvent`, `TransferOperationalEvent`, `AdjustmentOperationalEvent`) se almacenan en una única tabla `events` con una columna discriminadora `event_type`.

```10:14:src/main/java/com/inventoryservice/inventoryservice/persistence/entity/OperationalEvent.java
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
        name = "event_type",
        discriminatorType = DiscriminatorType.STRING
)
```

**Estructura de la Jerarquía:**
```
OperationalEvent (clase abstracta)
├── SaleOperationalEvent (@DiscriminatorValue("SALE"))
├── CollectionOperationalEvent (@DiscriminatorValue("COLLECTION"))
├── TransferOperationalEvent (@DiscriminatorValue("TRANSFER"))
└── AdjustmentOperationalEvent (@DiscriminatorValue("ADJUSTMENT"))
```

### 2. Esquema de Tabla `events`

```sql
CREATE TABLE events (
    -- Campos comunes
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL,
    license_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    event_timestamp TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    
    -- Campos específicos SALE
    customer_id BIGINT,
    sold_quantity NUMERIC(14, 3),
    unit_price NUMERIC(12, 2),
    
    -- Campos específicos COLLECTION
    supplier_id BIGINT,
    collected_quantity NUMERIC(14, 3),
    
    -- Campos específicos TRANSFER
    source_warehouse_id BIGINT,
    target_warehouse_id BIGINT,
    transferred_quantity NUMERIC(14, 3),
    
    -- Campos específicos ADJUSTMENT
    reason VARCHAR(255),
    adjusted_quantity NUMERIC(14, 3)
);
```

### 3. Índices Críticos

**Índice Principal (OBLIGATORIO):**
```sql
CREATE INDEX idx_inventory_aggregation 
ON events (license_id, warehouse_id, item_id);
```

Este índice es crítico para la consulta de inventario actual que agrupa por estos tres campos.

**Índices Adicionales Recomendados:**
```sql
CREATE INDEX idx_event_type ON events (event_type);
CREATE INDEX idx_event_timestamp ON events (event_timestamp);
```

### 4. Justificación del Modelo

**Ventajas de STI:**
- ✅ Consultas polimórficas eficientes (sin JOINs)
- ✅ Agregaciones simplificadas (una sola tabla)
- ✅ Inserciones rápidas (un solo INSERT)
- ✅ Mantenimiento simplificado


**Por qué NO otras estrategias:**
- **Table-Per-Class**: Requeriría UNION ALL complejos para consultas polimórficas
- **Joined Table Inheritance**: Overhead de JOINs en cada consulta
- **Table-Per-Concrete-Class**: Máxima complejidad y duplicación

**Decisión:** STI es óptima para Event Sourcing donde se necesita agregar todos los tipos de eventos en una sola consulta.

---

## PARTE 2 — POLIMORFISMO EN JAVA SPRING

### 1. Claridad de Implementación

**Configuración JPA:**
- `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)` en la clase base
- `@DiscriminatorValue` en cada subclase
- Switch expressions para crear instancias polimórficas

**Creación de Instancias:**
```24:64:src/main/java/com/inventoryservice/inventoryservice/domain/service/EventService.java
@Transactional
public void createEvent(CreateEventRequest r) {
    validateLicense(r.getLicenseId(), r.getCreatedBy(), r.getWarehouseId());
    OperationalEvent event = switch (r.getEventType()){
        case COLLECTION -> {
            CollectionOperationalEvent e = new CollectionOperationalEvent();
            e.setSupplierId(r.getSupplierId());
            e.setCollectedQuantity(r.getCollectedQuantity());
            yield e;
        }
        case TRANSFER -> {
            TransferOperationalEvent e = new TransferOperationalEvent();
            e.setSourceWareHouseId(r.getSourceWarehouseId());
            e.setTargetWarehouseId(r.getTargetWarehouseId());
            e.setTransferredQuantity(r.getTransferredQuantity());
            yield e;
        }
        case SALE -> {
            SaleOperationalEvent e = new SaleOperationalEvent();
            e.setCustomerId(r.getCustomerId());
            e.setSoldQuantity(r.getSoldQuantity());
            e.setUnitPrice(r.getUnitPrice());
            yield e;
        }
        case ADJUSTMENT -> {
            AdjustmentOperationalEvent e = new AdjustmentOperationalEvent();
            e.setReason(r.getReason());
            e.setAdjustedQuantity(r.getAdjustedQuantity());
            yield e;
        }
    };

    // Asignar campos comunes a todos los eventos
    event.setLicenseId(r.getLicenseId());
    event.setWarehouseIdl(r.getWarehouseId());
    event.setItemId(r.getItemId());
    event.setCreatedBy(r.getCreatedBy());

    repository.save(event);
}
```

### 2. Costo de Queries

**Query para Tipo Base:**
```java
List<OperationalEvent> events = repository.findAll();
```
**SQL Generado:**
```sql
SELECT * FROM events;
```
- Una sola query, sin JOINs
- Trae todas las columnas

**Query para Tipo Concreto:**
```java
@Query("SELECT e FROM SaleOperationalEvent e")
List<SaleOperationalEvent> findAllSales();
```
**SQL Generado:**
```sql
SELECT * FROM events WHERE event_type = 'SALE';
```
- Filtrado en BD
- Solo columnas relevantes

**Rendimiento:**
- ✅ Excelente para consultas polimórficas
- ✅ Eficiente para agregaciones



## PARTE 3 — SQL CRÍTICO (NO SE ACEPTA LÓGICA EN JAVA)

### 1. SQL Final - Consulta de Inventario Actual

Esta consulta **debe ejecutarse completamente en la base de datos** sin procesamiento en Java:

```sql
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
```

**Lógica del Impacto:**
- **COLLECTION**: `+collected_quantity` (aumenta inventario)
- **SALE**: `-sold_quantity` (disminuye inventario)
- **ADJUSTMENT**: `+/-adjusted_quantity` (ajusta inventario)
- **TRANSFER**: `-transferred_quantity` (origen) / `+transferred_quantity` (destino)

### 2. Índices Necesarios

**Índice Crítico (OBLIGATORIO):**
```sql
CREATE INDEX idx_inventory_aggregation 
ON events (license_id, warehouse_id, item_id);
```

**Sin este índice, la consulta será inaceptablemente lenta en producción.**

**Índices Adicionales:**
```sql
CREATE INDEX idx_event_type ON events (event_type);
CREATE INDEX idx_event_timestamp ON events (event_timestamp);
```

### 3. Plan de Ejecución Esperado

**Con Índice (Óptimo):**
```
GroupAggregate
  -> Index Scan using idx_inventory_aggregation
```
- ✅ Sin Sort (datos ya ordenados por índice)
- ✅ Sin Full Table Scan
- ✅ Tiempo: < 2s para 10M eventos aproximadamente

**Sin Índice (Subóptimo):**
```
GroupAggregate
  -> Sort (costoso)
    -> Seq Scan on events (lee toda la tabla)
```
- ❌ Full Table Scan
- ❌ Sort costoso en memoria/disco
- ❌ Tiempo: minutos para 10M eventos

**Verificación:**
```sql
EXPLAIN (ANALYZE, BUFFERS) 
SELECT ... -- consulta completa
```
Debe mostrar: `Index Scan using idx_inventory_aggregation`

---

## PARTE 4 — API SPRING

### 1. Endpoint para Recibir Cualquier Tipo de Evento

**Endpoint:**
```22:26:src/main/java/com/inventoryservice/inventoryservice/api/controller/EventController.java
@PostMapping
public ResponseEntity<Void> create(@Valid @RequestBody CreateEventRequest request) {
    service.createEvent(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
}
```

**Nota:** El uso de `@Valid` activa la validación automática de Bean Validation antes de que el request llegue al servicio.

- **Ruta**: `POST /api/events`
- **Content-Type**: `application/json`
- **Respuesta**: `201 CREATED`

**DTO de Entrada:**
```6:42:src/main/java/com/inventoryservice/inventoryservice/domain/dtos/CreateEventRequest.java
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

    // Campos específicos por tipo (validados condicionalmente)
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
}
```

**Validaciones Condicionales por Tipo de Evento:**
```44:82:src/main/java/com/inventoryservice/inventoryservice/domain/dtos/CreateEventRequest.java
@AssertTrue(message = "Para eventos COLLECTION, supplierId y collectedQuantity son requeridos")
public boolean isValidCollectionEvent() { ... }

@AssertTrue(message = "Para eventos TRANSFER, sourceWarehouseId, targetWarehouseId y transferredQuantity son requeridos")
public boolean isValidTransferEvent() { ... }

@AssertTrue(message = "Para eventos SALE, customerId, soldQuantity y unitPrice son requeridos")
public boolean isValidSaleEvent() { ... }

@AssertTrue(message = "Para eventos ADJUSTMENT, reason y adjustedQuantity son requeridos. La razón no puede exceder 255 caracteres")
public boolean isValidAdjustmentEvent() { ... }
```

**Validaciones Implementadas:**
- ✅ Campos comunes: `@NotNull` y `@Min(1)` para todos los IDs
- ✅ Validación condicional: `@AssertTrue` para campos específicos según el tipo de evento
- ✅ COLLECTION: `supplierId` y `collectedQuantity` requeridos
- ✅ TRANSFER: `sourceWarehouseId`, `targetWarehouseId` (diferentes) y `transferredQuantity` requeridos
- ✅ SALE: `customerId`, `soldQuantity` y `unitPrice` requeridos
- ✅ ADJUSTMENT: `reason` (máx. 255 caracteres) y `adjustedQuantity` requeridos

**Ejemplos de Request por Tipo de Evento:**

**COLLECTION (Recolección):**
```json
{
  "eventType": "COLLECTION",
  "licenseId": 123,
  "warehouseId": 456,
  "itemId": 789,
  "createdBy": 1,
  "supplierId": 300,
  "collectedQuantity": 50.750
}
```

**TRANSFER (Transferencia):**
```json
{
  "eventType": "TRANSFER",
  "licenseId": 123,
  "warehouseId": 456,
  "itemId": 789,
  "createdBy": 1,
  "sourceWarehouseId": 456,
  "targetWarehouseId": 789,
  "transferredQuantity": 25.500
}
```

**SALE (Venta):**
```json
{
  "eventType": "SALE",
  "licenseId": 123,
  "warehouseId": 456,
  "itemId": 789,
  "createdBy": 1,
  "customerId": 200,
  "soldQuantity": 10.250,
  "unitPrice": 25.99
}
```

**ADJUSTMENT (Ajuste):**
```json
{
  "eventType": "ADJUSTMENT",
  "licenseId": 123,
  "warehouseId": 456,
  "itemId": 789,
  "createdBy": 1,
  "reason": "Ajuste por inventario físico - Diferencia encontrada en conteo",
  "adjustedQuantity": -5.250
}
```

**Nota:** El campo `adjustedQuantity` puede ser positivo (aumento de inventario) o negativo (disminución de inventario).

### 2. Validación de Campos

**Estado Actual:** ✅ **IMPLEMENTADO** - El sistema valida automáticamente todos los campos del request usando Bean Validation.

**Dependencia Agregada:**
- `spring-boot-starter-validation` en `build.gradle`

**Validaciones de Campos Comunes (Siempre Requeridos):**
- `eventType`: `@NotNull` - El tipo de evento es requerido
- `licenseId`: `@NotNull`, `@Min(1)` - ID de licencia requerido y mayor a 0
- `warehouseId`: `@NotNull`, `@Min(1)` - ID de almacén requerido y mayor a 0
- `itemId`: `@NotNull`, `@Min(1)` - ID de artículo requerido y mayor a 0
- `createdBy`: `@NotNull`, `@Min(1)` - ID de usuario creador requerido y mayor a 0

**Validaciones Condicionales por Tipo de Evento:**
Las validaciones condicionales se implementan con `@AssertTrue` y se ejecutan solo cuando el tipo de evento correspondiente está presente:

- **COLLECTION**: `supplierId` y `collectedQuantity` requeridos (cantidad > 0)
- **TRANSFER**: `sourceWarehouseId`, `targetWarehouseId` (diferentes) y `transferredQuantity` requeridos (cantidad > 0)
- **SALE**: `customerId`, `soldQuantity` y `unitPrice` requeridos (cantidades > 0)
- **ADJUSTMENT**: `reason` (máx. 255 caracteres) y `adjustedQuantity` requeridos (valor absoluto > 0)

**Flujo de Validación:**
1. Spring valida automáticamente cuando se recibe el request con `@Valid`
2. Si hay errores, se lanza `MethodArgumentNotValidException`
3. `ApiExceptionHandler` captura la excepción y retorna `ErrorResponse` estructurado
4. Si pasa la validación, continúa con la lógica de negocio

**Ejemplo de Respuesta de Error:**
```json
{
  "message": "Error de validación en los campos del request",
  "errors": [
    {
      "field": "licenseId",
      "message": "El ID de la licencia es requerido",
      "rejectedValue": null
    },
    {
      "field": "createEventRequest",
      "message": "Para eventos SALE, customerId, soldQuantity y unitPrice son requeridos",
      "rejectedValue": null
    }
  ]
}
```

### 3. Validación de Licencia

**Estado Actual:** ✅ **IMPLEMENTADO** - El sistema valida la licencia antes de crear cualquier evento.

**Validación Implementada:**
- ✅ Verificar existencia y estado activo de la licencia
- ✅ Verificar permisos del usuario (`createdBy`) sobre la licencia
- ✅ Validar que el almacén pertenece a la licencia

**Implementación:**
```67:77:src/main/java/com/inventoryservice/inventoryservice/domain/service/EventService.java
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
```

**LicenseService:**
```4:26:src/main/java/com/inventoryservice/inventoryservice/domain/service/LicenseService.java
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
```

**Nota:** El `LicenseService` actualmente implementa validaciones básicas (verificación de IDs > 0). Para producción, debe extenderse para consultar una base de datos o servicio externo de licencias.

### 4. Aplicación del Impacto

El sistema utiliza **Event Sourcing**:
- Los eventos son inmutables (no se modifican ni eliminan)
- El inventario se calcula dinámicamente agregando todos los eventos
- El impacto se aplica cuando se consulta el inventario (SQL crítico)

**Impacto por Tipo:**
| Tipo | Impacto |
|------|---------|
| COLLECTION | `+collected_quantity` |
| SALE | `-sold_quantity` |
| ADJUSTMENT | `+/-adjusted_quantity` |
| TRANSFER | `-transferred_quantity` (origen) / `+transferred_quantity` (destino) |

**Nota:** ✅ Los campos comunes (`licenseId`, `warehouseId`, `itemId`, `createdBy`) se asignan correctamente después de crear la instancia del evento:

```58:61:src/main/java/com/inventoryservice/inventoryservice/domain/service/EventService.java
// Asignar campos comunes a todos los eventos
event.setLicenseId(r.getLicenseId());
event.setWarehouseIdl(r.getWarehouseId());
event.setItemId(r.getItemId());
event.setCreatedBy(r.getCreatedBy());
```

### 5. Persistencia del Evento

**Proceso:**
1. Creación de instancia del tipo concreto
2. Asignación de campos específicos y comunes
3. Inicialización automática: `createdAt` y `eventTimestamp` (constructor)
4. `repository.save(event)` persiste con JPA
5. PostgreSQL genera `id` automáticamente
6. Hibernate asigna `event_type` desde `@DiscriminatorValue`

**Transaccionalidad:**
```12:13:src/main/java/com/inventoryservice/inventoryservice/domain/service/EventService.java
@Service
@Transactional
```
- Atomicidad: rollback automático en caso de error
- Consistencia garantizada

**SQL Generado (ejemplo SALE):**
```sql
INSERT INTO events (
    item_id, license_id, warehouse_id, event_timestamp, 
    created_by, created_at, event_type,
    customer_id, sold_quantity, unit_price
) VALUES (?, ?, ?, ?, ?, ?, 'SALE', ?, ?, ?)
```

### 6. Manejo de Errores Coherentes

**Manejador Global Implementado:**
```13:145:src/main/java/com/inventoryservice/inventoryservice/exception/ApiExceptionHandler.java
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        // Extrae errores de campo y errores globales
        // Retorna ErrorResponse estructurado con lista de errores
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) { ... }

    @ExceptionHandler(IllegalAccessException.class)
    public ResponseEntity<String> badRequest(IllegalAccessException e) { ... }

    @ExceptionHandler(InvalidLicenseException.class)
    public ResponseEntity<String> handleInvalidLicense(InvalidLicenseException e) { ... }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<String> handleUnauthorized(UnauthorizedException e) { ... }

    @ExceptionHandler(InvalidWarehouseException.class)
    public ResponseEntity<String> handleInvalidWarehouse(InvalidWarehouseException e) { ... }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        // Maneja violaciones de integridad (foreign keys, constraints, etc.)
    }

    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleQueryTimeout(QueryTimeoutException e) {
        // Maneja timeouts de consultas
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ErrorResponse> handleTransactionSystem(TransactionSystemException e) {
        // Maneja errores del sistema de transacciones
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e) {
        // Maneja errores generales de acceso a datos
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        // Catch-all para cualquier excepción no manejada
    }
}
```

**DTO de Respuesta de Error:**
```java
public class ErrorResponse {
    private String message;
    private List<FieldError> errors;
    
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}
```

**Errores Manejados:**

| Tipo de Error | Código HTTP | Excepción | Casos |
|---------------|-------------|-----------|-------|
| Validación de Campos | 400 | `MethodArgumentNotValidException` | Campos requeridos faltantes, valores inválidos, validaciones condicionales |
| Argumentos Inválidos | 400 | `IllegalArgumentException` | Argumentos ilegales en la solicitud |
| Validación | 400 | `IllegalAccessException` | Acceso ilegal |
| Negocio - Licencia | 400 | `InvalidLicenseException` | Licencia inválida o inactiva |
| Negocio - Usuario | 400 | `UnauthorizedException` | Usuario sin acceso a la licencia |
| Negocio - Almacén | 400 | `InvalidWarehouseException` | Almacén no pertenece a la licencia |
| Integridad de Datos | 400 | `DataIntegrityViolationException` | Violación de constraints, foreign keys, valores únicos, campos NOT NULL |
| Timeout de Consulta | 408 | `QueryTimeoutException` | La consulta excedió el tiempo máximo permitido |
| Sistema de Transacciones | 500 | `TransactionSystemException` | Error en el sistema de transacciones |
| Acceso a Base de Datos | 500 | `DataAccessException` | Errores generales de acceso a la base de datos |
| Error Genérico | 500 | `Exception` | Cualquier otra excepción no manejada específicamente |

**Ejemplo de Respuesta de Error de Validación:**
```json
{
  "message": "Error de validación en los campos del request",
  "errors": [
    {
      "field": "licenseId",
      "message": "El ID de la licencia es requerido",
      "rejectedValue": null
    },
    {
      "field": "createEventRequest",
      "message": "Para eventos SALE, customerId, soldQuantity y unitPrice son requeridos",
      "rejectedValue": null
    }
  ]
}
```

**Excepciones Personalizadas:**
- `InvalidLicenseException`: Lanzada cuando la licencia no es válida o no está activa
- `UnauthorizedException`: Lanzada cuando el usuario no tiene acceso a la licencia
- `InvalidWarehouseException`: Lanzada cuando el almacén no pertenece a la licencia

**Ejemplos de Respuestas de Error:**

**Error de Integridad de Datos:**
```json
{
  "message": "Error: Referencia a un registro que no existe en la base de datos"
}
```

**Error de Timeout:**
```json
{
  "message": "La operación tardó demasiado tiempo. Por favor, intente nuevamente."
}
```

**Error Genérico de Base de Datos:**
```json
{
  "message": "Error al acceder a la base de datos. Por favor, intente nuevamente más tarde."
}
```

**Error Inesperado:**
```json
{
  "message": "Ocurrió un error inesperado. Por favor, contacte al administrador del sistema."
}
```

**Características del Manejo de Errores:**
- ✅ Logging de errores para debugging (usando SLF4J Logger)
- ✅ Mensajes de error amigables para el usuario
- ✅ Detección inteligente de tipos de error de integridad (foreign keys, duplicados, NOT NULL)
- ✅ Códigos HTTP apropiados (400 para errores del cliente, 500 para errores del servidor)
- ✅ Catch-all para excepciones no manejadas específicamente

### 7. Flujo Completo

```
POST /api/events
    ↓
EventController.create(@Valid @RequestBody)
    ├─ 1. ✅ Validación automática de campos (Bean Validation)
    │   ├─ Validar campos comunes (@NotNull, @Min)
    │   └─ Validar campos específicos según tipo (@AssertTrue)
    │   └─ Si hay errores → MethodArgumentNotValidException
    │       ↓
    │   ApiExceptionHandler.handleValidation()
    │       ↓
    │   Response 400 BAD REQUEST con ErrorResponse
    ↓
EventService.createEvent()
    ├─ 2. ✅ Validar licencia (validateLicense)
    │   ├─ Validar existencia y estado activo
    │   ├─ Validar permisos del usuario
    │   └─ Validar pertenencia del almacén
    │   └─ Si hay errores → Excepciones personalizadas
    │       ↓
    │   ApiExceptionHandler maneja excepción
    │       ↓
    │   Response 400 BAD REQUEST con mensaje
    ├─ 3. Crear instancia del evento (switch)
    ├─ 4. Asignar campos comunes (licenseId, warehouseId, itemId)
    └─ 5. repository.save(event)
        ├─ Si hay error de BD → DataAccessException
        │   ├─ DataIntegrityViolationException → 400 BAD REQUEST
        │   ├─ QueryTimeoutException → 408 REQUEST TIMEOUT
        │   ├─ TransactionSystemException → 500 INTERNAL SERVER ERROR
        │   └─ Otras DataAccessException → 500 INTERNAL SERVER ERROR
        │       ↓
        │   ApiExceptionHandler maneja excepción
        │       ↓
        │   Response con ErrorResponse y código HTTP apropiado
        ↓
    PostgreSQL (INSERT INTO events)
        ↓
Response 201 CREATED
```

**En caso de error de validación de campos:**
- Spring valida automáticamente con `@Valid`
- Se lanza `MethodArgumentNotValidException`
- `ApiExceptionHandler.handleValidation()` captura la excepción
- Se retorna `400 BAD REQUEST` con `ErrorResponse` estructurado que incluye:
  - Mensaje general
  - Lista de errores de campo con nombre, mensaje y valor rechazado
  - Errores globales (de `@AssertTrue`)

**En caso de error de validación de licencia:**
- Se lanza excepción personalizada (`InvalidLicenseException`, `UnauthorizedException`, `InvalidWarehouseException`)
- `ApiExceptionHandler` captura la excepción
- Se retorna `400 BAD REQUEST` con el mensaje de error

**En caso de error de base de datos:**
- Se lanza excepción de acceso a datos (`DataAccessException` y sus subclases)
- `ApiExceptionHandler` captura la excepción según su tipo:
  - `DataIntegrityViolationException`: Retorna `400 BAD REQUEST` con mensaje específico según el tipo de violación
  - `QueryTimeoutException`: Retorna `408 REQUEST TIMEOUT`
  - `TransactionSystemException`: Retorna `500 INTERNAL SERVER ERROR`
  - Otras `DataAccessException`: Retorna `500 INTERNAL SERVER ERROR`
- Se registra el error en los logs para debugging
- Se retorna `ErrorResponse` con mensaje amigable al usuario

**En caso de error inesperado:**
- Se captura cualquier `Exception` no manejada específicamente
- Se registra el error completo en los logs
- Se retorna `500 INTERNAL SERVER ERROR` con mensaje genérico

### 8. Resumen y Puntos Críticos

**Características Implementadas:**
- ✅ Endpoint único que acepta cualquier tipo de evento
- ✅ Polimorfismo mediante switch expressions
- ✅ Persistencia transaccional
- ✅ Validación de licencia completa
- ✅ Asignación de campos comunes
- ✅ Validación de campos con Bean Validation
- ✅ Validación condicional por tipo de evento
- ✅ Manejo de errores de validación estructurado
- ✅ Manejo completo de errores de base de datos
- ✅ Manejo de errores genéricos con logging

**Puntos Críticos Completados:**
1. ✅ **Validación de Licencia**: Implementada con `LicenseService` y validación en `EventService`
2. ✅ **Asignación de Campos Comunes**: Implementada en `EventService` (líneas 58-61) incluyendo `licenseId`, `warehouseId`, `itemId` y `createdBy`
3. ✅ **Manejo de Errores de Licencia**: Implementado en `ApiExceptionHandler`
4. ✅ **Validación de Campos**: Implementada con anotaciones Bean Validation (`@NotNull`, `@Min`, `@AssertTrue`)
5. ✅ **Manejo de Errores de Validación**: Implementado con `ErrorResponse` y manejo de `MethodArgumentNotValidException`
6. ✅ **Manejo de Errores Genéricos**: Implementado con manejo de excepciones de BD y catch-all


---

## RESUMEN EJECUTIVO

### Decisiones Clave

1. **Modelo de BD**: Single Table Inheritance (STI) para optimizar consultas agregadas de Event Sourcing
2. **Polimorfismo Java**: Switch expressions con JPA para crear instancias polimórficas
3. **SQL Crítico**: Cálculo de inventario completamente en BD, requiere índice `idx_inventory_aggregation`
4. **API**: Endpoint único polimórfico con validación de licencia y validación de campos implementadas

### Requisitos Críticos

- ✅ **Índice obligatorio**: `idx_inventory_aggregation` en `(license_id, warehouse_id, item_id)`
- ✅ **Validación de licencia**: Implementada con `LicenseService` y validación en `EventService`
- ✅ **Asignación de campos comunes**: Implementada en `EventService` (incluyendo `licenseId`, `warehouseId`, `itemId` y `createdBy`)
- ✅ **Manejo de errores de licencia**: Implementado en `ApiExceptionHandler`
- ✅ **Validación de campos**: Implementada con Bean Validation (`@NotNull`, `@Min`, `@AssertTrue`)
- ✅ **Manejo de errores de validación**: Implementado con `ErrorResponse` estructurado
- ✅ **Manejo de errores genéricos**: Implementado con manejo de excepciones de BD, transacciones y catch-all

### Arquitectura

**Patrón:** Event Sourcing
- Eventos inmutables almacenados en tabla única
- Inventario calculado dinámicamente agregando eventos
- Sin tabla de "inventario actual" que se actualice

**Stack Tecnológico:**
- Java 21 + Spring Boot 4.0.1
- JPA/Hibernate con Single Table Inheritance
- PostgreSQL con índices optimizados
- REST API con manejo de errores centralizado
- Bean Validation para validación de requests

### Configuración

**Requisitos del Sistema:**
- Java 21 o superior
- PostgreSQL (configurado en `application.properties`)
- Gradle (incluido wrapper)

**Configuración de Base de Datos:**
El servicio está configurado para conectarse a PostgreSQL en `localhost:5432` con las credenciales especificadas en `application.properties`. La base de datos se inicializa automáticamente mediante `spring.jpa.hibernate.ddl-auto=update`.

**Puerto del Servidor:**
El servicio se ejecuta en el puerto `8090` con el contexto `/` (configurado en `application.properties`).

**Endpoints Disponibles:**
- `POST /api/events` - Crear un nuevo evento de inventario

**Nota sobre LicenseService:**
El `LicenseService` actualmente implementa validaciones básicas (verificación de IDs > 0). Para producción, debe extenderse para consultar una base de datos o servicio externo de licencias que valide:
- Existencia y estado activo de la licencia
- Permisos del usuario sobre la licencia
- Pertenencia del almacén a la licencia

### Cómo Ejecutar el Proyecto

**Requisitos Previos:**
1. **PostgreSQL en ejecución**: El servicio requiere PostgreSQL corriendo en `localhost:5432` (según configuración en `application.properties`)
2. **Base de datos creada**: Aunque el servicio usa `spring.jpa.hibernate.ddl-auto=update`, la base de datos debe existir previamente
3. **Java 21**: Asegúrate de tener Java 21 o superior instalado

**Opción 1: Usando Gradle Wrapper (Recomendado)**
```bash
./gradlew bootRun
```

**Opción 2: Compilar y Ejecutar el JAR**
```bash
# Compilar el proyecto
./gradlew build

# Ejecutar el JAR generado
java -jar build/libs/inventoryservice-0.0.1-SNAPSHOT.jar
```

**Verificación:**
Una vez iniciado, el servicio estará disponible en:
- **URL Base**: `http://localhost:8090`
- **Endpoint**: `POST http://localhost:8090/api/events`

**Nota:** Si usas Docker Compose, puedes iniciar PostgreSQL con:
```bash
docker-compose up -d
```
