package com.adela.payments.utils;

import com.adela.payments.dto.SearchCriteria;
import com.adela.payments.specifications.GenericSpecification;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Set;

public class SpecificationBuilder {

    private SpecificationBuilder() {}

    public static <T> Specification<T> buildSpecification(String filter, Set<String> allowedFields) {
        List<SearchCriteria> criteriaList = FilterParser.parse(filter);

        Specification<T> spec = Specification.unrestricted();
        for (SearchCriteria criteria : criteriaList) {
            spec = spec.and(new GenericSpecification<T>(criteria, allowedFields));
        }
        return spec;
    }
}
