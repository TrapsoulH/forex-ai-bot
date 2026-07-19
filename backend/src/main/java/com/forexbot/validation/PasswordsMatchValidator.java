package com.forexbot.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, Object> {

    private String passwordField;
    private String confirmField;

    @Override
    public void initialize(PasswordsMatch ann) {
        this.passwordField = ann.password();
        this.confirmField  = ann.confirmPassword();
    }

    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext ctx) {
        Object pw  = new BeanWrapperImpl(obj).getPropertyValue(passwordField);
        Object cpw = new BeanWrapperImpl(obj).getPropertyValue(confirmField);
        boolean match = pw != null && pw.equals(cpw);
        if (!match) {
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
               .addPropertyNode(confirmField)
               .addConstraintViolation();
        }
        return match;
    }
}
