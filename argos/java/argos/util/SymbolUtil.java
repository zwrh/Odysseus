/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.util;

import java.util.regex.Pattern;

/**
 * Utility methods for working with Ghidra symbols.
 */
public class SymbolUtil {
    // Regular expressions for Ghidra's default naming patterns
    private static final Pattern DEFAULT_NAME_PATTERN = Pattern.compile(
        "^(FUN|LAB|SUB|DAT|EXT|PTR|ARRAY)_[0-9a-fA-F]+$"
    );

    /**
     * Check if a symbol name appears to be a default Ghidra-generated name
     * @param name The symbol name to check
     * @return True if the name follows Ghidra's default naming patterns
     */
    public static boolean isDefaultSymbolName(String name) {
        if (name == null) {
            return false;
        }

        return DEFAULT_NAME_PATTERN.matcher(name).matches();
    }
}
