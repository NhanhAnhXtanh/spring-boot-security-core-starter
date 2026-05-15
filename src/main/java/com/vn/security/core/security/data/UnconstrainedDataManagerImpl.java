package com.vn.security.core.security.data;

import com.vn.security.core.security.fetch.FetchPlan;
import com.vn.security.core.security.fetch.FetchPlanMetadataTools;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trusted data access implementation that bypasses all security enforcement.
 *
 * <p>This is the explicit trusted bypass path per D-02. It exposes raw repository
 * access with no CRUD checks, no row-level policies, no attribute filtering, and
 * no fetch-plan shaping. Use only from internal infrastructure code that has
 * already enforced its own access controls (e.g., row-policy evaluation, system tasks).
 */
@Service
@Transactional
public class UnconstrainedDataManagerImpl implements UnconstrainedDataManager {

    private static final String FETCH_GRAPH_HINT = "jakarta.persistence.fetchgraph";

    /** Matches {@code select <projection> from} at the start of a JPQL query (ignoring leading whitespace). */
    private static final Pattern SELECT_FROM_PATTERN = Pattern.compile(
        "^\\s*select\\s+(.+?)\\s+from\\s+",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /** Matches an {@code order by ...} tail clause. */
    private static final Pattern ORDER_BY_TAIL_PATTERN = Pattern.compile(
        "\\s+order\\s+by\\s+.+$",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final EntityManager entityManager;

    public UnconstrainedDataManagerImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Load an entity by class and id with no security checks.
     *
     * @throws EntityNotFoundException if no entity with the given id exists
     */
    @Override
    @Transactional(readOnly = true)
    public <T> T load(Class<T> entityClass, Object id) {
        T entity = entityManager.find(entityClass, id);
        if (entity == null) {
            throw new EntityNotFoundException(entityClass.getSimpleName() + " not found: " + id);
        }
        return entity;
    }

    /**
     * Load all entities of the given class with no security checks.
     */
    @Override
    @Transactional(readOnly = true)
    public <T> List<T> loadAll(Class<T> entityClass) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        cq.from(entityClass);
        return entityManager.createQuery(cq).getResultList();
    }

    /**
     * Load one entity matching the given specification with no security checks.
     */
    @Override
    @Transactional(readOnly = true)
    public <T> Optional<T> loadOne(Class<T> entityClass, Specification<T> spec) {
        List<T> results = createQuery(entityClass, spec, Sort.unsorted()).setMaxResults(2).getResultList();
        if (results.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, results.size());
        }
        return results.stream().findFirst();
    }

    /**
     * Load all entities matching the given specification with no security checks.
     */
    @Override
    @Transactional(readOnly = true)
    public <T> List<T> loadList(Class<T> entityClass, Specification<T> spec) {
        return createQuery(entityClass, spec, Sort.unsorted()).getResultList();
    }

    /**
     * Load a page of entities matching the given specification with no security checks.
     */
    @Override
    @Transactional(readOnly = true)
    public <T> Page<T> loadPage(Class<T> entityClass, Specification<T> spec, Pageable pageable) {
        TypedQuery<T> query = createQuery(entityClass, spec, pageable.getSort());
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(query.getResultList(), pageable, count(entityClass, spec));
    }

    @Override
    @Transactional(readOnly = true)
    public <T> List<T> loadListByJpql(Class<T> entityClass, String jpql, Map<String, Object> parameters, FetchPlan fetchPlan) {
        TypedQuery<T> query = entityManager.createQuery(jpql, entityClass);
        applyParameters(query, parameters);
        applyFetchPlan(query, entityClass, fetchPlan);
        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public <T> Page<T> loadPageByJpql(
        Class<T> entityClass,
        String jpql,
        Map<String, Object> parameters,
        FetchPlan fetchPlan,
        Pageable pageable
    ) {
        TypedQuery<T> query = entityManager.createQuery(jpql, entityClass);
        applyParameters(query, parameters);
        applyFetchPlan(query, entityClass, fetchPlan);

        if (pageable != null && pageable.isPaged()) {
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
            long total = countByJpql(jpql, parameters);
            return new PageImpl<>(query.getResultList(), pageable, total);
        }

        List<T> results = query.getResultList();
        Pageable effective = pageable != null ? pageable : Pageable.unpaged();
        return new PageImpl<>(results, effective, results.size());
    }

    private void applyParameters(TypedQuery<?> query, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        Set<String> declared = query
            .getParameters()
            .stream()
            .map(Parameter::getName)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        parameters.forEach((name, value) -> {
            if (declared.contains(name)) {
                query.setParameter(name, value);
            }
        });
    }

    private <T> void applyFetchPlan(TypedQuery<T> query, Class<T> entityClass, FetchPlan fetchPlan) {
        if (fetchPlan == null) {
            return;
        }
        EntityGraph<T> graph = FetchPlanMetadataTools.toEntityGraph(entityManager, entityClass, fetchPlan);
        query.setHint(FETCH_GRAPH_HINT, graph);
    }

    private long countByJpql(String jpql, Map<String, Object> parameters) {
        String countJpql = rewriteToCount(jpql);
        TypedQuery<Long> query = entityManager.createQuery(countJpql, Long.class);
        applyParameters(query, parameters);
        return query.getSingleResult();
    }

    /**
     * Rewrites a JPQL select query into a count query by replacing the projection with
     * {@code count(<root-alias>)} and stripping a trailing {@code order by} clause.
     *
     * <p>The root alias is parsed out of the first {@code from <Entity> <alias>} token. Returns
     * the original JPQL prefixed with a count over its root alias; correctness is not preserved
     * for {@code distinct} or {@code join fetch} on collection associations — those callers must
     * supply an explicit count query when added in a future overload.
     */
    static String rewriteToCount(String jpql) {
        if (jpql == null || jpql.isBlank()) {
            throw new IllegalArgumentException("JPQL must not be blank");
        }
        Matcher selectMatcher = SELECT_FROM_PATTERN.matcher(jpql);
        if (!selectMatcher.find()) {
            throw new IllegalArgumentException("Unable to derive count query from JPQL: " + jpql);
        }
        String afterFrom = jpql.substring(selectMatcher.end());
        String rootAlias = extractRootAlias(afterFrom);
        String stripped = ORDER_BY_TAIL_PATTERN.matcher(jpql).replaceFirst("");
        return SELECT_FROM_PATTERN.matcher(stripped).replaceFirst("select count(" + rootAlias + ") from ");
    }

    private static String extractRootAlias(String afterFrom) {
        String[] tokens = afterFrom.trim().split("\\s+", 3);
        if (tokens.length < 2) {
            throw new IllegalArgumentException("JPQL FROM clause must declare a root alias");
        }
        return tokens[1];
    }

    /**
     * Create a new entity instance with no security checks.
     */
    @Override
    public <T> T create(Class<T> entityClass) {
        try {
            return entityClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create entity: " + entityClass.getName(), e);
        }
    }

    /**
     * Save an entity with no permission checks.
     */
    @Override
    public <T> T save(T entity) {
        if (entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity) == null) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    /**
     * Delete an entity by class and id with no permission checks.
     *
     * @throws EntityNotFoundException if no entity with the given id exists
     */
    @Override
    @SuppressWarnings("unchecked")
    public void delete(Class<?> entityClass, Object id) {
        Object entity = load((Class<Object>) entityClass, id);
        delete(entity);
    }

    /**
     * Delete an entity instance with no permission checks.
     */
    @Override
    public void delete(Object entity) {
        entityManager.remove(entityManager.contains(entity) ? entity : entityManager.merge(entity));
    }

    private <T> TypedQuery<T> createQuery(Class<T> entityClass, Specification<T> spec, Sort sort) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);
        applySpec(spec, root, cq, cb);
        applySort(sort, root, cq, cb);
        return entityManager.createQuery(cq);
    }

    private <T> long count(Class<T> entityClass, Specification<T> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<T> root = cq.from(entityClass);
        applySpec(spec, root, cq, cb);
        cq.select(cb.count(root));
        return entityManager.createQuery(cq).getSingleResult();
    }

    private <T> void applySpec(Specification<T> spec, Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (spec == null) {
            return;
        }
        Predicate predicate = spec.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    private <T> void applySort(Sort sort, Root<T> root, CriteriaQuery<T> query, CriteriaBuilder cb) {
        if (sort == null || sort.isUnsorted()) {
            return;
        }
        List<Order> orders = sort
            .stream()
            .map(order -> order.isAscending() ? cb.asc(root.get(order.getProperty())) : cb.desc(root.get(order.getProperty())))
            .toList();
        query.orderBy(orders);
    }
}
