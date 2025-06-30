package com.company.payroll.exception;

/**
 * Custom unchecked exception for data access layer.
 */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
    public DataAccessException(String message) {
        super(message);
    }
    public DataAccessException(Throwable cause) {
        super(cause);
    }
}