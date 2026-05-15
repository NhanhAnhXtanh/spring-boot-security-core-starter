package com.vn.security.core.service;

import com.vn.security.core.domain.Organization;
import com.vn.security.core.service.dto.OrganizationDTO;
import com.vn.security.core.service.dto.OrganizationDetailDTO;
import com.vn.security.core.service.mapper.OrganizationMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Benchmark baseline service that reads organizations through direct EntityManager flow.
 */
@Service
@Transactional(readOnly = true)
public class BenchmarkOrganizationStandardService {

    private static final Logger LOG = LoggerFactory.getLogger(BenchmarkOrganizationStandardService.class);

    private final EntityManager entityManager;
    private final OrganizationMapper organizationMapper;

    public BenchmarkOrganizationStandardService(EntityManager entityManager, OrganizationMapper organizationMapper) {
        this.entityManager = entityManager;
        this.organizationMapper = organizationMapper;
    }

    public Page<OrganizationDTO> list(Pageable pageable) {
        LOG.debug("Benchmark request to list organizations with standard flow");
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Organization> cq = cb.createQuery(Organization.class);
        Root<Organization> root = cq.from(Organization.class);
        applySort(pageable.getSort(), root, cq, cb);
        List<Organization> rows = entityManager
            .createQuery(cq)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();
        return new PageImpl<>(rows, pageable, countOrganizations()).map(organizationMapper::toDto);
    }

    public Optional<OrganizationDetailDTO> findOne(Long id) {
        LOG.debug("Benchmark request to get organization with standard flow : {}", id);
        return Optional.ofNullable(entityManager.find(Organization.class, id)).map(organizationMapper::toDetailDto);
    }

    private long countOrganizations() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Organization> root = cq.from(Organization.class);
        cq.select(cb.count(root));
        return entityManager.createQuery(cq).getSingleResult();
    }

    private void applySort(Sort sort, Root<Organization> root, CriteriaQuery<Organization> query, CriteriaBuilder cb) {
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
