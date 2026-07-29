package com.adela.payments.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Set;

public class EmailDomainValidator implements ConstraintValidator<NonDisposableEmail, String> {

    private final Set<String> blocked;

    public EmailDomainValidator(@Value("${app.validation.disposable-email}") List<String> domains) {
        this.blocked = Set.copyOf(domains);
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || !email.contains("@")) {
            return true;
        }
        final int atIndex = email.lastIndexOf('@') + 1;
        final int dotIndex = email.lastIndexOf('.');
        final String domain = email.substring(atIndex, dotIndex).toLowerCase();
        return !this.blocked.contains(domain);
    }

}
