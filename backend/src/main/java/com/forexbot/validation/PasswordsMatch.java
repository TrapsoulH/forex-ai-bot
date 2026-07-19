package com.forexbot.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordsMatchValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordsMatch {
    String message() default "Passwords do not match";
    String password();
    String confirmPassword();
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
