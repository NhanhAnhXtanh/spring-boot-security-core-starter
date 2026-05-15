package com.vn.security.core.web.rest.errors;

import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Extension of Spring's {@link ProblemDetail} adding a cause chain.
 */
public class ProblemDetailWithCause extends ProblemDetail {

    private ProblemDetailWithCause cause;

    protected ProblemDetailWithCause(int rawStatusCode) {
        super(rawStatusCode);
    }

    public ProblemDetailWithCause getCause() {
        return cause;
    }

    public void setCause(ProblemDetailWithCause cause) {
        this.cause = cause;
    }

    public static ProblemDetailWithCauseBuilder instance() {
        return new ProblemDetailWithCauseBuilder();
    }

    public static final class ProblemDetailWithCauseBuilder {

        private int status = 500;
        private URI type;
        private String title;
        private String detail;
        private final Map<String, Object> properties = new HashMap<>();

        private ProblemDetailWithCauseBuilder() {}

        public ProblemDetailWithCauseBuilder withStatus(int status) {
            this.status = status;
            return this;
        }

        public ProblemDetailWithCauseBuilder withType(URI type) {
            this.type = type;
            return this;
        }

        public ProblemDetailWithCauseBuilder withTitle(String title) {
            this.title = title;
            return this;
        }

        public ProblemDetailWithCauseBuilder withDetail(String detail) {
            this.detail = detail;
            return this;
        }

        public ProblemDetailWithCauseBuilder withProperty(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        public ProblemDetailWithCause build() {
            ProblemDetailWithCause pd = new ProblemDetailWithCause(status);
            if (type != null) pd.setType(type);
            if (title != null) pd.setTitle(title);
            if (detail != null) pd.setDetail(detail);
            properties.forEach(pd::setProperty);
            return pd;
        }
    }
}
