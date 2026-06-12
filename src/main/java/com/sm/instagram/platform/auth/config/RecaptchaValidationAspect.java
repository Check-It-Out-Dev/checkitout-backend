package com.sm.instagram.platform.auth.config;

import com.sm.instagram.platform.auth.annotation.RequiresRecaptcha;
import com.sm.instagram.platform.auth.dto.AssessmentResult;
import com.sm.instagram.platform.auth.service.RecaptchaService;
import com.sm.instagram.platform.common.exceptions.RecaptchaValidationException;
import com.sm.instagram.platform.config.RecaptchaConfig;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Aspect to intercept methods annotated with @RequiresRecaptcha
 * and validate reCAPTCHA tokens
 */
@Slf4j
@Aspect
@Component
public class RecaptchaValidationAspect {
    
    @Autowired
    private RecaptchaService recaptchaService;
    
    @Autowired
    private RecaptchaConfig recaptchaConfig;
    
    @Around("@annotation(requiresRecaptcha)")
    public Object validateRecaptcha(ProceedingJoinPoint joinPoint, RequiresRecaptcha requiresRecaptcha) throws Throwable {
        
        // Skip validation if reCAPTCHA is disabled globally
        if (!recaptchaConfig.isEnabled()) {
            log.debug("reCAPTCHA validation skipped - globally disabled");
            return joinPoint.proceed();
        }
        
        // Get the current HTTP request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("No request context available for reCAPTCHA validation");
            if (requiresRecaptcha.enforceValidation()) {
                throw new RecaptchaValidationException("Request context not available");
            }
            return joinPoint.proceed();
        }
        
        HttpServletRequest request = attributes.getRequest();
        
        // Get reCAPTCHA token from header
        String recaptchaToken = request.getHeader("X-Recaptcha-Token");
        
        // If no token and enforcement is required
        if ((recaptchaToken == null || recaptchaToken.isEmpty()) && requiresRecaptcha.enforceValidation()) {
            log.warn("No reCAPTCHA token provided for action: {}", requiresRecaptcha.action());
            throw new RecaptchaValidationException("reCAPTCHA token is required");
        }
        
        // Validate the token
        try {
            AssessmentResult result = recaptchaService.verifyToken(
                recaptchaToken, 
                requiresRecaptcha.action(), 
                request
            );
            
            // Check if validation passed
            if (!result.isAllowed()) {
                log.warn("reCAPTCHA validation failed for action '{}': {}", 
                    requiresRecaptcha.action(), result.getReason());
                
                if (requiresRecaptcha.enforceValidation()) {
                    throw new RecaptchaValidationException(
                        "reCAPTCHA validation failed: " + result.getReason(),
                        result.getScore(),
                        requiresRecaptcha.action()
                    );
                }
            }
            
            // Check custom minimum score if specified
            if (requiresRecaptcha.minScore() >= 0 && result.getScore() < requiresRecaptcha.minScore()) {
                log.warn("reCAPTCHA score {} below custom threshold {} for action '{}'", 
                    result.getScore(), requiresRecaptcha.minScore(), requiresRecaptcha.action());
                
                if (requiresRecaptcha.enforceValidation()) {
                    throw new RecaptchaValidationException(
                        "reCAPTCHA score below threshold",
                        result.getScore(),
                        requiresRecaptcha.action()
                    );
                }
            }
            
            // GDPR logging for validation attempts
            log.info("GDPR: Operation=recaptcha_validation, Action={}, Score={}, Purpose=bot_prevention, LegalBasis=security",
                requiresRecaptcha.action(), result.getScore());
            
            log.info("reCAPTCHA validation successful for action '{}' with score {}", 
                requiresRecaptcha.action(), result.getScore());
            
        } catch (RecaptchaValidationException e) {
            // Re-throw validation exceptions
            throw e;
        } catch (Exception e) {
            log.error("Error during reCAPTCHA validation: {}", e.getMessage());
            if (requiresRecaptcha.enforceValidation()) {
                throw new RecaptchaValidationException("reCAPTCHA validation error", e);
            }
        }
        
        // Proceed with the original method
        return joinPoint.proceed();
    }
}
