package com.vn.security.core.service;

import com.vn.security.core.domain.ProofDepartment;
import com.vn.security.core.domain.ProofEmployee;
import com.vn.security.core.security.data.SecureDataManager;
import com.vn.security.core.security.data.SecureDataManager.EntityMutation;
import com.vn.security.core.security.data.SecuredLoadQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Secured ProofEmployee application service backed only by {@link SecureDataManager}.
 */
@Service
@Transactional
public class ProofEmployeeService {

    private static final Logger LOG = LoggerFactory.getLogger(ProofEmployeeService.class);
    private static final Class<ProofEmployee> ENTITY_CLASS = ProofEmployee.class;
    private static final String ENTITY_CODE = "proof-employee";
    private static final String LIST_FETCH_PLAN = "proof-employee-list";

    private final SecureDataManager secureDataManager;
    private final EntityManager entityManager;

    public ProofEmployeeService(SecureDataManager secureDataManager, EntityManager entityManager) {
        this.secureDataManager = secureDataManager;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<ProofEmployee> list(Pageable pageable) {
        LOG.debug("Request to list proof employees");
        return secureDataManager.loadList(ENTITY_CLASS, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<ProofEmployee> findOne(Long id) {
        LOG.debug("Request to get proof employee : {}", id);
        return secureDataManager.loadOne(ENTITY_CLASS, id);
    }

    public ProofEmployee create(EntityMutation<ProofEmployee> mutation) {
        LOG.debug("Request to create proof employee");
        return secureDataManager.save(ENTITY_CLASS, null, normalizeMutation(mutation));
    }

    public ProofEmployee update(Long id, EntityMutation<ProofEmployee> mutation) {
        LOG.debug("Request to update proof employee : {}", id);
        return secureDataManager.save(ENTITY_CLASS, id, normalizeMutation(mutation));
    }

    public ProofEmployee patch(Long id, EntityMutation<ProofEmployee> mutation) {
        LOG.debug("Request to patch proof employee : {}", id);
        return secureDataManager.save(ENTITY_CLASS, id, normalizeMutation(mutation));
    }

    @Transactional(readOnly = true)
    public Page<ProofEmployee> query(String fetchPlanCode, Pageable pageable, Map<String, Object> filters) {
        LOG.debug("Request to query proof employees");
        SecuredLoadQuery query = new SecuredLoadQuery(
            ENTITY_CODE,
            null,
            filters,
            pageable,
            pageable.getSort(),
            resolveFetchPlanCode(fetchPlanCode, LIST_FETCH_PLAN)
        );
        return secureDataManager.loadByQuery(ENTITY_CLASS, query);
    }

    public void delete(Long id) {
        LOG.debug("Request to delete proof employee : {}", id);
        secureDataManager.delete(ENTITY_CLASS, id);
    }

    private String resolveFetchPlanCode(String fetchPlanCode, String defaultFetchPlanCode) {
        return fetchPlanCode == null || fetchPlanCode.isBlank() ? defaultFetchPlanCode : fetchPlanCode;
    }

    private EntityMutation<ProofEmployee> normalizeMutation(EntityMutation<ProofEmployee> mutation) {
        ProofEmployee employee = requireEntity(mutation);
        adaptDepartmentReference(employee, mutation.changedAttributes());
        return mutation;
    }

    private ProofEmployee requireEntity(EntityMutation<ProofEmployee> mutation) {
        if (mutation == null || mutation.entity() == null) {
            throw new IllegalArgumentException("Typed proof employee mutation is required");
        }
        return mutation.entity();
    }

    private void adaptDepartmentReference(ProofEmployee employee, Collection<String> changedAttributes) {
        if (changedAttributes == null || !changedAttributes.contains("department")) {
            return;
        }

        ProofDepartment requestedDepartment = employee.getDepartment();
        Long departmentId = requestedDepartment != null ? requestedDepartment.getId() : null;
        if (departmentId == null) {
            throw new IllegalArgumentException("employee.department reference requires an id");
        }

        secureDataManager
            .loadOne(ProofDepartment.class, departmentId)
            .orElseThrow(() ->
                new AccessDeniedException("ProofDepartment reference not found or not accessible: " + departmentId)
            );

        ProofDepartment department = entityManager.find(ProofDepartment.class, departmentId);
        if (department == null) {
            throw new EntityNotFoundException("ProofDepartment not found: " + departmentId);
        }
        employee.setDepartment(department);
    }
}
