/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.capabilities;

/**
 * Exception thrown when program validation fails.
 * This exception is used to indicate various program-related errors such as:
 * - Program not found
 * - Program is in an invalid state (e.g., closed)
 * - Invalid program path provided
 */
public class ProgramValidationException extends RuntimeException {
    
    public ProgramValidationException(String message) {
        super(message);
    }
    
    public ProgramValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
