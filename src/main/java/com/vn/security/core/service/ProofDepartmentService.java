package com.vn.security.core.service;

import com.vn.security.core.domain.ProofDepartment;
import com.vn.security.core.domain.ProofOrganization;
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
 * Secured ProofDepartment application service backed only by {@link SecureDataManager}.
 */
@Service
@Transactional
public class ProofDepartmentService {

    private static final Logger LOG = LoggerFactory.getLogger(ProofDepartmentService.class);
    private static final Class<ProofDepartment> ENTITY_CLASS = ProofDepartment.class;
    private static final String ENTITY_CODE = "proof-department";
    private static final String LIST_FETCH_PLAN = "proof-department-list";

    private final SecureDataManager secureDataManager;
    private final EntityManager entityManager;

    public ProofDepartmentService(SecureDataManager secureDataManager, EntityManager entityManager) {
        this.secureDataManager = secureDataManager;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<ProofDepartment> list(Pageable pageable) {
        LOG.debug("Request to list proof departments");
        return secureDataManager.loadList(ENTITY_CLASS, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<ProofDepartment> findOne(Long id) {
        LOG.debug("Request to get proof department : {}", id);
        return secureDataManager.loadOne(ENTITY_CLASS, id);
    }

    public ProofDepartment create(EntityMutation<ProofDepartment> mutation) {
        LOG.debug("Request to create proof department");
        return secureDataManager.save(ENTITY_CLASS, null, normalizeMutation(mutation));
    }

    public ProofDepartment update(Long id, EntityMutation<ProofDepartment> mutation) {
        LOG.debug("Request to update proof department : {}", id);
        return secureDataManager.save(ENTITY_CLASS, id, normalizeMutation(mutation));
    }

    public ProofDepartment patch(Long id, EntityMutation<ProofDepartment> mutation) {
        LOG.debug("Request to patch proof department : {}", id);
        return secureDataManager.save(ENTITY_CLASS, id, normalizeMutation(mutation));
    }

    @Transactional(readOnly = true)
    public Page<ProofDepartment> query(String fetchPlanCode, Pageable pageable, Map<String, Object> filters) {
        LOG.debug("Request to query proof departments");
        SecuredLoadQuery query = SecuredLoadQuery
            .builder()
            .entityCode(ENTITY_CODE)
            .jpql("select d from ProofDepartment d where d.organization.id = 3951 order by d.name")
            .parameters(filters)
            .pageable(pageable)
            .sort(pageable.getSort())
            .fetchPlanCode(resolveFetchPlanCode(fetchPlanCode, LIST_FETCH_PLAN))
            .build();
        return secureDataManager.loadByQuery(ENTITY_CLASS, query);
    }

    public void delete(Long id) {
        LOG.debug("Request to delete proof department : {}", id);
        secureDataManager.delete(ENTITY_CLASS, id);
    }

    private String resolveFetchPlanCode(String fetchPlanCode, String defaultFetchPlanCode) {
        return fetchPlanCode == null || fetchPlanCode.isBlank() ? defaultFetchPlanCode : fetchPlanCode;
    }

    private EntityMutation<ProofDepartment> normalizeMutation(EntityMutation<ProofDepartment> mutation) {
        ProofDepartment department = requireEntity(mutation);
        adaptOrganizationReference(department, mutation.changedAttributes());
        return mutation;
    }

    private ProofDepartment requireEntity(EntityMutation<ProofDepartment> mutation) {
        if (mutation == null || mutation.entity() == null) {
            throw new IllegalArgumentException("Typed proof department mutation is required");
        }
        return mutation.entity();
    }

    private void adaptOrganizationReference(ProofDepartment department, Collection<String> changedAttributes) {
        if (changedAttributes == null || !changedAttributes.contains("organization")) {
            return;
        }

        ProofOrganization requestedOrganization = department.getOrganization();
        Long organizationId = requestedOrganization != null ? requestedOrganization.getId() : null;
        if (organizationId == null) {
            throw new IllegalArgumentException("department.organization reference requires an id");
        }

        secureDataManager
            .loadOne(ProofOrganization.class, organizationId)
            .orElseThrow(() ->
                new AccessDeniedException("ProofOrganization reference not found or not accessible: " + organizationId)
            );

        ProofOrganization organization = entityManager.find(ProofOrganization.class, organizationId);
        if (organization == null) {
            throw new EntityNotFoundException("ProofOrganization not found: " + organizationId);
        }
        department.setOrganization(organization);
    }
}
