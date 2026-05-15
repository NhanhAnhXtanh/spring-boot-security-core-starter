package com.vn.security.core.security.data;

import com.vn.security.core.security.fetch.FetchPlan;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Trusted data access interface that bypasses security enforcement.
 * Use only from internal infrastructure code that has already enforced
 * its own access controls (e.g., row-policy evaluation, system tasks).
 */
public interface UnconstrainedDataManager {
    /**
     * Load an entity by class and id with no security checks.
     */
    <T> T load(Class<T> entityClass, Object id);

    /**
     * Load all entities of the given class with no security checks.
     */
    <T> List<T> loadAll(Class<T> entityClass);

    /**
     * Load one entity matching the given specification with no security checks.
     */
    <T> Optional<T> loadOne(Class<T> entityClass, Specification<T> spec);

    /**
     * Load all entities matching the given specification with no security checks.
     */
    <T> List<T> loadList(Class<T> entityClass, Specification<T> spec);

    /**
     * Load a page of entities matching the given specification with no security checks.
     */
    <T> Page<T> loadPage(Class<T> entityClass, Specification<T> spec, Pageable pageable);

    /**
     * Execute a raw JPQL select query with the given bind parameters, applying an optional
     * {@link FetchPlan} as a JPA fetch graph hint. No security checks are performed.
     *
     * @param entityClass result entity type (must match the SELECT in the JPQL)
     * @param jpql        JPQL select query (e.g. {@code "select c from Customer c where c.email like :email"})
     * @param parameters  named bind parameters; values are passed through verbatim
     * @param fetchPlan   optional fetch plan applied via {@code jakarta.persistence.fetchgraph}; may be null
     * @return all rows produced by the query
     */
    <T> List<T> loadListByJpql(Class<T> entityClass, String jpql, Map<String, Object> parameters, FetchPlan fetchPlan);

    /**
     * Page variant of {@link #loadListByJpql}. The total count is derived from the given JPQL
     * by replacing the projection with {@code count(<alias>)} and stripping any {@code order by}
     * clause; queries using {@code distinct} or {@code join fetch} on collections will produce
     * a count that does not match the rendered page size and should pass an explicit count
     * query via a future overload instead.
     */
    <T> Page<T> loadPageByJpql(
        Class<T> entityClass,
        String jpql,
        Map<String, Object> parameters,
        FetchPlan fetchPlan,
        Pageable pageable
    );

    /**
     * Create a new entity instance with no security checks.
     */
    <T> T create(Class<T> entityClass);

    /**
     * Save an entity with no permission checks.
     */
    <T> T save(T entity);

    /**
     * Delete an entity instance with no permission checks.
     */
    void delete(Object entity);

    /**
     * Delete an entity by class and id with no permission checks.
     */
    void delete(Class<?> entityClass, Object id);
}
