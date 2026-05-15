package com.vn.security.core.web.rest.errors;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

@SuppressWarnings("java:S110")
public class RoleInUseException extends ErrorResponseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RoleInUseException() {
        super(
            HttpStatus.CONFLICT,
            ProblemDetailWithCause.instance()
                .withStatus(HttpStatus.CONFLICT.value())
                .withType(ErrorConstants.DEFAULT_TYPE)
                .withTitle("Role is assigned to users and cannot be deleted")
                .withProperty("message", "error.roleinuse")
                .withProperty("params", "secRole")
                .build(),
            null
        );
    }
}
