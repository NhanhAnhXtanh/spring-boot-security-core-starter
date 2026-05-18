package com.vn.security.core.service;

import com.vn.security.core.domain.ProofOrganization;
import com.vn.security.core.security.SecurityUtils;
import com.vn.security.core.security.data.SecureDataManager;
import com.vn.security.core.security.data.SecureDataManager.EntityMutation;
import com.vn.security.core.security.data.SecuredLoadQuery;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Secured ProofOrganization application service backed only by {@link SecureDataManager}.
 */
@Service
@Transactional
public class ProofOrganizationService {

    private static final Logger LOG = LoggerFactory.getLogger(ProofOrganizationService.class);
    private static final Class<ProofOrganization> ENTITY_CLASS = ProofOrganization.class;
    private static final String ENTITY_CODE = "proof-organization";
    private static final String LIST_FETCH_PLAN = "proof-organization-list";

    private final SecureDataManager secureDataManager;

    public ProofOrganizationService(SecureDataManager secureDataManager) {
        this.secureDataManager = secureDataManager;
    }

    @Transactional(readOnly = true)
    public Page<ProofOrganization> list(Pageable pageable) {
        LOG.debug("Request to list proof organizations");
        return secureDataManager.loadList(ENTITY_CLASS, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<ProofOrganization> findOne(Long id) {
        LOG.debug("Request to get proof organization : {}", id);
        return secureDataManager.loadOne(ENTITY_CLASS, id);
    }

    public ProofOrganization create(EntityMutation<ProofOrganization> mutation) {
        LOG.debug("Request to create proof organization");
        requireEntity(mutation);
        if (mutation.entity().getOwnerLogin() == null) {
            String currentLogin = SecurityUtils.getCurrentUserLogin().orElseThrow();
            mutation.entity().setOwnerLogin(currentLogin);
            ArrayList<String> attrs = new ArrayList<>(mutation.changedAttributes());
            attrs.add("ownerLogin");
            mutation = new EntityMutation<>(mutation.entity(), attrs);
        }
        return secureDataManager.save(ENTITY_CLASS, null, mutation);
    }

    public ProofOrganization update(Long id, EntityMutation<ProofOrganization> mutation) {
        LOG.debug("Request to update proof organization : {}", id);
        requireEntity(mutation);
        return secureDataManager.save(ENTITY_CLASS, id, mutation);
    }

    public ProofOrganization patch(Long id, EntityMutation<ProofOrganization> mutation) {
        LOG.debug("Request to patch proof organization : {}", id);
        requireEntity(mutation);
        return secureDataManager.save(ENTITY_CLASS, id, mutation);
    }

    @Transactional(readOnly = true)
    public Page<ProofOrganization> query(String fetchPlanCode, Pageable pageable, Map<String, Object> filters) {
        LOG.debug("Request to query proof organizations");
        SecuredLoadQuery query = new SecuredLoadQuery(
            ENTITY_CODE,
            null,
            filters,
            pageable,
            pageable.getSort(),
            fetchPlanCode != null && !fetchPlanCode.isBlank() ? fetchPlanCode : LIST_FETCH_PLAN
        );
        return secureDataManager.loadByQuery(ENTITY_CLASS, query);
    }

    public void delete(Long id) {
        LOG.debug("Request to delete proof organization : {}", id);
        secureDataManager.delete(ENTITY_CLASS, id);
    }

    private void requireEntity(EntityMutation<ProofOrganization> mutation) {
        if (mutation == null || mutation.entity() == null) {
            throw new IllegalArgumentException("Typed proof organization mutation is required");
        }
    }
}
