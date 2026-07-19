package com.forexbot.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that the domain part of an email address has at least one MX record.
 * Falls back to an A/AAAA record check. Fails open if DNS is unreachable.
 */
@Documented
@Constraint(validatedBy = EmailDomainValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmailDomain {
    String message() default "Email domain does not appear to accept mail. Please check the address.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
