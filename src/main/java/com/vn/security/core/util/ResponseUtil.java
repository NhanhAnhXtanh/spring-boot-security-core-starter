package com.vn.security.core.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

public final class ResponseUtil {

    private ResponseUtil() {}

    public static <X> ResponseEntity<X> wrapOrNotFound(Optional<X> maybeResponse) {
        return wrapOrNotFound(maybeResponse, null);
    }

    public static <X> ResponseEntity<X> wrapOrNotFound(Optional<X> maybeResponse, HttpHeaders header) {
        return maybeResponse
            .map(response -> {
                ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
                if (header != null) {
                    builder.headers(header);
                }
                return builder.body(response);
            })
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
