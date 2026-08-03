package com.adela.payments.utils;

import com.adela.payments.exception.BadRequestException;
import com.adela.payments.request.CustomPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public class PageUtil {

    public PageUtil() {}

    public static Pageable toPageable(CustomPageRequest request, Set<String> allowedSortFields) {
        if (!allowedSortFields.contains(request.getSortBy())) {
            throw new BadRequestException("Unsupported sort field: " + request.getSortBy());
        }
        int size = Math.min(request.getSize(), 10);
        int page = Math.max(request.getPage(), 0);
        Sort sort = Sort.by(Sort.Direction.fromString(request.getSortDirection()), request.getSortBy());
        return PageRequest.of(page, size, sort);
    }
}
