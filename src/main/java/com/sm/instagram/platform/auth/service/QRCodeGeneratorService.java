package com.sm.instagram.platform.auth.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating QR codes with embedded logos for 2FA setup.
 * Creates high-quality QR codes that are easily scannable by authenticator apps.
 */
@Service
@Slf4j
public class QRCodeGeneratorService {
    
    private static final int QR_CODE_SIZE = 300;
    private static final int LOGO_SIZE = 60;
    private static final int LOGO_BORDER = 5;
    
    @Value("${qrcode.logo.path:static/favicon.png}")
    private String logoPath;
    
    @Value("${qrcode.color.foreground:#000000}")
    private String foregroundColor;
    
    @Value("${qrcode.color.background:#FFFFFF}")
    private String backgroundColor;
    
    /**
     * Generate a QR code image with embedded logo for TOTP authentication.
     * 
     * @param otpAuthUrl The otpauth:// URL containing all 2FA information
     * @return Base64 encoded PNG image of the QR code
     */
    public String generateQRCodeWithLogo(String otpAuthUrl) {
        try {
            // Generate the QR code
            BufferedImage qrCodeImage = generateQRCode(otpAuthUrl);
            
            // Add logo to the QR code
            BufferedImage qrCodeWithLogo = embedLogo(qrCodeImage);
            
            // Convert to Base64
            return encodeToBase64(qrCodeWithLogo);
            
        } catch (Exception e) {
            log.error("Failed to generate QR code with logo, falling back to simple QR code", e);
            // Fallback to simple QR code without logo
            return generateSimpleQRCode(otpAuthUrl);
        }
    }
    
    /**
     * Generate a simple QR code without logo (fallback).
     * 
     * @param otpAuthUrl The otpauth:// URL
     * @return Base64 encoded PNG image
     */
    public String generateSimpleQRCode(String otpAuthUrl) {
        try {
            BufferedImage qrCodeImage = generateQRCode(otpAuthUrl);
            return encodeToBase64(qrCodeImage);
        } catch (Exception e) {
            log.error("Failed to generate QR code", e);
            throw new ValidationTranslatableException("error.validation.failed");
        }
    }
    
    /**
     * Generate the base QR code image.
     * 
     * @param content The content to encode
     * @return BufferedImage of the QR code
     */
    private BufferedImage generateQRCode(String content) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        
        // Configure QR code generation
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // High error correction for logo overlay
        hints.put(EncodeHintType.MARGIN, 1); // Small margin
        
        // Generate QR code matrix
        BitMatrix bitMatrix = qrCodeWriter.encode(
            content,
            BarcodeFormat.QR_CODE,
            QR_CODE_SIZE,
            QR_CODE_SIZE,
            hints
        );
        
        // Convert to BufferedImage with custom colors
        MatrixToImageConfig config = new MatrixToImageConfig(
            parseColor(foregroundColor),
            parseColor(backgroundColor)
        );
        
        return MatrixToImageWriter.toBufferedImage(bitMatrix, config);
    }
    
    /**
     * Embed a logo in the center of the QR code.
     * 
     * @param qrCodeImage The QR code image
     * @return QR code with embedded logo
     */
    private BufferedImage embedLogo(BufferedImage qrCodeImage) {
        try {
            // Load logo
            BufferedImage logo = loadLogo();
            if (logo == null) {
                log.warn("Logo not found, returning QR code without logo");
                return qrCodeImage;
            }
            
            // Create a copy of the QR code to modify
            BufferedImage combined = new BufferedImage(
                QR_CODE_SIZE,
                QR_CODE_SIZE,
                BufferedImage.TYPE_INT_ARGB
            );
            
            Graphics2D g = combined.createGraphics();
            
            // Enable anti-aliasing for better quality
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            
            // Draw the QR code
            g.drawImage(qrCodeImage, 0, 0, null);
            
            // Scale logo to fit
            Image scaledLogo = logo.getScaledInstance(LOGO_SIZE, LOGO_SIZE, Image.SCALE_SMOOTH);
            
            // Calculate position to center the logo
            int logoX = (QR_CODE_SIZE - LOGO_SIZE) / 2;
            int logoY = (QR_CODE_SIZE - LOGO_SIZE) / 2;
            
            // Draw white background for logo (improves contrast)
            g.setColor(Color.WHITE);
            g.fillRoundRect(
                logoX - LOGO_BORDER,
                logoY - LOGO_BORDER,
                LOGO_SIZE + (2 * LOGO_BORDER),
                LOGO_SIZE + (2 * LOGO_BORDER),
                15,
                15
            );
            
            // Create a circular clip for the logo (optional, for round logos)
            Shape clip = new RoundRectangle2D.Float(
                logoX,
                logoY,
                LOGO_SIZE,
                LOGO_SIZE,
                10,
                10
            );
            g.setClip(clip);
            
            // Draw the logo
            g.drawImage(scaledLogo, logoX, logoY, null);
            
            g.dispose();
            
            return combined;
            
        } catch (Exception e) {
            log.error("Failed to embed logo in QR code", e);
            return qrCodeImage;
        }
    }
    
    /**
     * Load the logo image from resources.
     * 
     * @return BufferedImage of the logo or null if not found
     */
    private BufferedImage loadLogo() {
        try {
            // Try to load from classpath
            Resource resource = new ClassPathResource(logoPath);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return ImageIO.read(is);
                }
            }
            
            // Try alternative paths
            String[] alternativePaths = {
                "static/images/favicon.png",
                "static/img/favicon.png",
                "public/favicon.png",
                "favicon.png",
                "static/logo.png",
                "logo.png"
            };
            
            for (String path : alternativePaths) {
                resource = new ClassPathResource(path);
                if (resource.exists()) {
                    try (InputStream is = resource.getInputStream()) {
                        log.info("Logo found at: {}", path);
                        return ImageIO.read(is);
                    }
                }
            }
            
            log.warn("No logo file found in any expected location");
            return null;
            
        } catch (IOException e) {
            log.error("Error loading logo", e);
            return null;
        }
    }
    
    /**
     * Convert BufferedImage to Base64 encoded PNG.
     * 
     * @param image The image to encode
     * @return Base64 string with data URL prefix
     */
    private String encodeToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();
        
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        return "data:image/png;base64," + base64;
    }
    
    /**
     * Parse hex color string to integer.
     * 
     * @param hexColor Hex color string (e.g., "#FF0000" or "FF0000")
     * @return Integer representation of the color
     */
    private int parseColor(String hexColor) {
        if (hexColor.startsWith("#")) {
            hexColor = hexColor.substring(1);
        }
        
        try {
            // Parse as ARGB (with alpha channel)
            if (hexColor.length() == 6) {
                hexColor = "FF" + hexColor; // Add full opacity
            }
            return (int) Long.parseLong(hexColor, 16);
        } catch (NumberFormatException e) {
            log.warn("Invalid color format: {}, using default", hexColor);
            return hexColor.equals(backgroundColor) ? 0xFFFFFFFF : 0xFF000000;
        }
    }
    
    /**
     * Generate QR code data URL for direct embedding in HTML.
     * This is useful for frontend that wants to display the QR code directly.
     * 
     * @param otpAuthUrl The otpauth:// URL
     * @param withLogo Whether to include the logo
     * @return Complete data URL that can be used in <img src="">
     */
    public String generateQRCodeDataUrl(String otpAuthUrl, boolean withLogo) {
        if (withLogo) {
            return generateQRCodeWithLogo(otpAuthUrl);
        } else {
            return generateSimpleQRCode(otpAuthUrl);
        }
    }
    
    /**
     * Validate that the OTP Auth URL contains all required information.
     * 
     * @param otpAuthUrl The URL to validate
     * @return true if valid, false otherwise
     */
    public boolean validateOtpAuthUrl(String otpAuthUrl) {
        if (otpAuthUrl == null || !otpAuthUrl.startsWith("otpauth://totp/")) {
            return false;
        }
        
        // Check for required parameters
        boolean hasSecret = otpAuthUrl.contains("secret=");
        boolean hasIssuer = otpAuthUrl.contains("issuer=");
        
        if (!hasSecret) {
            log.error("OTP Auth URL missing secret parameter");
            return false;
        }
        
        if (!hasIssuer) {
            log.warn("OTP Auth URL missing issuer parameter");
        }
        
        return true;
    }
}
