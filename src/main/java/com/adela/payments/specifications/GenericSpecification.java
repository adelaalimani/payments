package com.adela.payments.specifications;

import com.adela.payments.dto.SearchCriteria;
import com.adela.payments.enums.FilterOperation;
import com.adela.payments.exception.BadRequestException;
import jakarta.persistence.criteria.*;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GenericSpecification<T> implements Specification<T> {

    private final SearchCriteria criteria;
    private final Set<String> allowedFields;

    public GenericSpecification(SearchCriteria criteria, Set<String> allowedFields) {
        this.criteria = criteria;
        this.allowedFields = allowedFields;
    }

    @Override
    public @Nullable Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        String key = criteria.getKey();

        if (!allowedFields.contains(key)) {
            throw new BadRequestException("Unknown or unsupported filter field: " + key);
        }

        Path<?> path = root.get(key);

        try {
            if (criteria.getOperation() == FilterOperation.IN) {
                List<?> values = parseInValues(path.getJavaType(), criteria.getValue());
                return path.in(values);
            }

            Object typedValue = convertValue(path.getJavaType(), criteria.getValue());

            return switch (criteria.getOperation()) {
                case EQ -> cb.equal(path, typedValue);
                case NE -> cb.notEqual(path, typedValue);
                case GT -> cb.greaterThan((Path<Comparable>) path, (Comparable) typedValue);
                case LT -> cb.lessThan((Path<Comparable>) path, (Comparable) typedValue);
                case GTE -> cb.greaterThanOrEqualTo((Path<Comparable>) path, (Comparable) typedValue);
                case LTE -> cb.lessThanOrEqualTo((Path<Comparable>) path, (Comparable) typedValue);
                case LIKE -> cb.like(cb.lower(path.as(String.class)),
                        "%" + typedValue.toString().toLowerCase() + "%");
                case IN -> throw new IllegalStateException("IN handled above");
            };
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BadRequestException(
                    "Invalid value '" + criteria.getValue() + "' for field '" + key + "'");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertValue(Class<?> targetType, String raw) {
        if (targetType.equals(BigDecimal.class)) return new BigDecimal(raw);
        if (targetType.equals(Long.class) || targetType.equals(long.class)) return Long.valueOf(raw);
        if (targetType.equals(Integer.class) || targetType.equals(int.class)) return Integer.valueOf(raw);
        if (targetType.isEnum()) return Enum.valueOf((Class<Enum>) targetType, raw.toUpperCase());
        if (targetType.equals(LocalDateTime.class)) return LocalDateTime.parse(raw);
        if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) return Boolean.valueOf(raw);
        return raw;
    }

    private List<?> parseInValues(Class<?> targetType, String raw) {
        return Arrays.stream(raw.split("\\|"))
                .map(v -> convertValue(targetType, v.trim()))
                .collect(Collectors.toList());
    }
}
