package com.adela.payments.response;

import com.adela.payments.enums.PaymentMethod;
import com.adela.payments.enums.PaymentStatus;

import java.math.BigDecimal;

public record PaymentResponse(Long id,
                              BigDecimal amount,
                              PaymentStatus status,
                              PaymentMethod method,
                              String reference) {
}
