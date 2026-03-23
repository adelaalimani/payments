package com.adela.payments.dto;

import com.adela.payments.enums.PaymentMethod;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record CreatePaymentRequest(

        @NotNull
        @DecimalMin(value = "1.00", message = "Value must be greater than 1")
        BigDecimal amount,

        @NotNull
        PaymentMethod method) {

}
