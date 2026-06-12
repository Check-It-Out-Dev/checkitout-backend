package com.sm.instagram.platform.common.util;

/**
 * Simple HTML encoder to prevent XSS attacks.
 * Encodes user input to prevent script injection.
 */
public class HtmlEncoder {
    
    private HtmlEncoder() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Encode string for safe HTML output
     * @param input the string to encode
     * @return HTML-safe encoded string
     */
    public static String encode(String input) {
        if (input == null) {
            return null;
        }
        
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;");
    }
    
    /**
     * Encode object's string representation for safe HTML output
     * @param input the object to encode
     * @return HTML-safe encoded string
     */
    public static String encode(Object input) {
        if (input == null) {
            return null;
        }
        return encode(input.toString());
    }
}
