package com.vn.security.core.security.data;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Query object carrying all parameters needed for a secured entity load.
 *
 * <p>Construct via {@link #builder()} for readability:
 * <pre>{@code
 * SecuredLoadQuery q = SecuredLoadQuery.builder()
 *     .entityCode("department")
 *     .jpql("select d from Department d where d.organization.id = :orgId")
 *     .parameter("orgId", 1L)
 *     .pageable(pageable)
 *     .fetchPlanCode("department-detail")
 *     .build();
 * }</pre>
 */
public record SecuredLoadQuery(
    String entityCode,
    String jpql,
    Map<String, Object> parameters,
    Pageable pageable,
    Sort sort,
    String fetchPlanCode
) {
    public SecuredLoadQuery {
        parameters = parameters == null ? Map.of() : parameters;
        sort = sort == null && pageable != null ? pageable.getSort() : sort;
    }

    /**
     * Convenience factory for simple paginated catalog reads.
     */
    public static SecuredLoadQuery of(String entityCode, String fetchPlanCode, Pageable pageable) {
        return new SecuredLoadQuery(entityCode, null, Map.of(), pageable, pageable.getSort(), fetchPlanCode);
    }

    /** Start a new fluent builder for {@code SecuredLoadQuery}. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String entityCode;
        private String jpql;
        private Map<String, Object> parameters = new LinkedHashMap<>();
        private Pageable pageable;
        private Sort sort;
        private String fetchPlanCode;

        private Builder() {}

        public Builder entityCode(String entityCode) {
            this.entityCode = entityCode;
            return this;
        }

        public Builder jpql(String jpql) {
            this.jpql = jpql;
            return this;
        }

        /** Replace the entire bind-parameter map. {@code null} becomes an empty map. */
        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
            return this;
        }

        /** Add a single named bind parameter. Subsequent calls overwrite the same name. */
        public Builder parameter(String name, Object value) {
            this.parameters.put(name, value);
            return this;
        }

        public Builder pageable(Pageable pageable) {
            this.pageable = pageable;
            return this;
        }

        public Builder sort(Sort sort) {
            this.sort = sort;
            return this;
        }

        public Builder fetchPlanCode(String fetchPlanCode) {
            this.fetchPlanCode = fetchPlanCode;
            return this;
        }

        public SecuredLoadQuery build() {
            return new SecuredLoadQuery(entityCode, jpql, parameters, pageable, sort, fetchPlanCode);
        }
    }
}
