package com.vn.security.core.security.data;

import com.vn.security.core.security.fetch.FetchPlan;
import com.vn.security.core.security.fetch.FetchPlanResolver;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Executes a {@link SecuredLoadQuery} that carries a JPQL select statement.
 *
 * <p>Pure execution path — assumes the caller has already enforced the
 * {@code jpqlAllowed} catalog gate and the CRUD {@code READ} permission check.
 * Resolves the optional fetch plan and forwards to {@link UnconstrainedDataManager}.
 */
@Component
public class SecureJpqlQueryExecutor {

    private final UnconstrainedDataManager unconstrainedDataManager;
    private final FetchPlanResolver fetchPlanResolver;

    public SecureJpqlQueryExecutor(UnconstrainedDataManager unconstrainedDataManager, FetchPlanResolver fetchPlanResolver) {
        this.unconstrainedDataManager = unconstrainedDataManager;
        this.fetchPlanResolver = fetchPlanResolver;
    }

    public <E> Page<E> execute(Class<E> entityClass, SecuredLoadQuery query) {
        FetchPlan fetchPlan = resolveFetchPlan(entityClass, query.fetchPlanCode());
        return unconstrainedDataManager.loadPageByJpql(
            entityClass,
            query.jpql(),
            query.parameters(),
            fetchPlan,
            query.pageable()
        );
    }

    private FetchPlan resolveFetchPlan(Class<?> entityClass, String fetchPlanCode) {
        if (fetchPlanCode == null || fetchPlanCode.isBlank()) {
            return null;
        }
        return fetchPlanResolver.resolve(entityClass, fetchPlanCode);
    }
}
