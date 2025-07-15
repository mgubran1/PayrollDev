package com.company.exception;

/**
 * Exception thrown when there's an error accessing or manipulating data
 */
public class DataAccessException extends RuntimeException {
    
    public DataAccessException(String message) {
        super(message);
    }
    
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DataAccessException(Throwable cause) {
        super(cause);
    }
}