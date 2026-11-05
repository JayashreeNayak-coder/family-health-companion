package com.familyhealth.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/** The list response format from docs/05 section 2. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {

	public static final int MAX_SIZE = 100;

	public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements());
	}

	public static Pageable request(int page, int size, Sort sort) {
		if (page < 0 || size < 1 || size > MAX_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page must be 0 or more, and size 1 to 100");
		}
		return PageRequest.of(page, size, sort);
	}

}
