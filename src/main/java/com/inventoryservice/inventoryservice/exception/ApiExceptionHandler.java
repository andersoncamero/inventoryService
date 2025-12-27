package com.inventoryservice.inventoryservice.exception;

import com.inventoryservice.inventoryservice.domain.dtos.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ErrorResponse.FieldError(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getRejectedValue()
                ))
                .collect(Collectors.toList());

        List<ErrorResponse.FieldError> globalErrors = e.getBindingResult()
                .getGlobalErrors()
                .stream()
                .map(error -> new ErrorResponse.FieldError(
                        error.getObjectName(),
                        error.getDefaultMessage(),
                        null
                ))
                .collect(Collectors.toList());

        fieldErrors.addAll(globalErrors);

        ErrorResponse errorResponse = new ErrorResponse(
                "Error de validación en los campos del request",
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("Argumento ilegal recibido: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                "Error en los argumentos de la solicitud: " + e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(IllegalAccessException.class)
    public ResponseEntity<String> badRequest(IllegalAccessException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(InvalidLicenseException.class)
    public ResponseEntity<String> handleInvalidLicense(InvalidLicenseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<String> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(InvalidWarehouseException.class)
    public ResponseEntity<String> handleInvalidWarehouse(InvalidWarehouseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        logger.error("Violación de integridad de datos", e);
        String message = "Error de integridad de datos. Verifique que los datos proporcionados sean válidos.";
        
        // Intentar extraer un mensaje más específico si es posible
        Throwable rootCause = e.getRootCause();
        if (rootCause != null && rootCause.getMessage() != null) {
            String rootMessage = rootCause.getMessage();
            if (rootMessage.contains("foreign key") || rootMessage.contains("FK_")) {
                message = "Error: Referencia a un registro que no existe en la base de datos";
            } else if (rootMessage.contains("unique") || rootMessage.contains("duplicate")) {
                message = "Error: Intento de crear un registro duplicado";
            } else if (rootMessage.contains("not null") || rootMessage.contains("NULL")) {
                message = "Error: Campo requerido no puede ser nulo";
            }
        }
        
        ErrorResponse errorResponse = new ErrorResponse(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleQueryTimeout(QueryTimeoutException e) {
        logger.error("Timeout en consulta a la base de datos", e);
        ErrorResponse errorResponse = new ErrorResponse(
                "La operación tardó demasiado tiempo. Por favor, intente nuevamente."
        );
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(errorResponse);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ErrorResponse> handleTransactionSystem(TransactionSystemException e) {
        logger.error("Error en el sistema de transacciones", e);
        ErrorResponse errorResponse = new ErrorResponse(
                "Error al procesar la transacción. Por favor, intente nuevamente."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e) {
        logger.error("Error de acceso a la base de datos", e);
        ErrorResponse errorResponse = new ErrorResponse(
                "Error al acceder a la base de datos. Por favor, intente nuevamente más tarde."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        logger.error("Error inesperado en el servidor", e);
        ErrorResponse errorResponse = new ErrorResponse(
                "Ocurrió un error inesperado. Por favor, contacte al administrador del sistema."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
