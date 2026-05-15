package com.vn.security.core.security.store;

import com.vn.security.core.security.domain.SecPermission;
import com.vn.security.core.security.permission.TargetType;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class SecPermissionStore {

    private final EntityManager entityManager;

    public SecPermissionStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public SecPermission save(SecPermission entity) {
        if (entity.getId() == null) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    public void deleteAll(Collection<SecPermission> entities) {
        entities.forEach(this::delete);
    }

    public void delete(SecPermission entity) {
        entityManager.remove(entityManager.contains(entity) ? entity : entityManager.merge(entity));
    }

    public void deleteByAuthorityName(String authorityName) {
        entityManager
            .createQuery("delete from SecPermission p where p.authorityName = :authorityName")
            .setParameter("authorityName", authorityName)
            .executeUpdate();
    }

    @Transactional(readOnly = true)
    public Optional<SecPermission> findById(Long id) {
        return Optional.ofNullable(entityManager.find(SecPermission.class, id));
    }

    @Transactional(readOnly = true)
    public List<SecPermission> findAll() {
        return entityManager.createQuery("select p from SecPermission p order by p.id asc", SecPermission.class).getResultList();
    }

    @Transactional(readOnly = true)
    public List<SecPermission> findByAuthorityName(String authorityName) {
        return entityManager
            .createQuery("select p from SecPermission p where p.authorityName = :authorityName order by p.id asc", SecPermission.class)
            .setParameter("authorityName", authorityName)
            .getResultList();
    }

    @Transactional(readOnly = true)
    public List<SecPermission> findAllByAuthorityNameAndTargetTypeAndTargetAndActionOrderByIdAsc(
        String authorityName,
        TargetType targetType,
        String target,
        String action
    ) {
        return entityManager
            .createQuery(
                "select p from SecPermission p " +
                    "where p.authorityName = :authorityName " +
                    "and p.targetType = :targetType " +
                    "and p.target = :target " +
                    "and p.action = :action " +
                    "order by p.id asc",
                SecPermission.class
            )
            .setParameter("authorityName", authorityName)
            .setParameter("targetType", targetType)
            .setParameter("target", target)
            .setParameter("action", action)
            .getResultList();
    }

    @Transactional(readOnly = true)
    public List<SecPermission> findByRolesAndTargets(
        Collection<String> authorityNames,
        TargetType targetType,
        Collection<String> targets,
        String action
    ) {
        if (authorityNames.isEmpty() || targets.isEmpty()) {
            return List.of();
        }
        return entityManager
            .createQuery(
                "select p from SecPermission p " +
                    "where p.authorityName in :authorityNames " +
                    "and p.targetType = :targetType " +
                    "and p.target in :targets " +
                    "and p.action = :action",
                SecPermission.class
            )
            .setParameter("authorityNames", authorityNames)
            .setParameter("targetType", targetType)
            .setParameter("targets", targets)
            .setParameter("action", action)
            .getResultList();
    }

    @Transactional(readOnly = true)
    public List<SecPermission> findAllByAuthorityNameIn(Collection<String> authorityNames) {
        if (authorityNames.isEmpty()) {
            return List.of();
        }
        return entityManager
            .createQuery("select p from SecPermission p where p.authorityName in :authorityNames", SecPermission.class)
            .setParameter("authorityNames", authorityNames)
            .getResultList();
    }

    public void deleteSpecificEntityPermissions(String authorityName, String action, String effect) {
        entityManager
            .createQuery(
                "delete from SecPermission p " +
                    "where p.authorityName = :authorityName " +
                    "and p.targetType = :targetType " +
                    "and p.target != '*' " +
                    "and p.action = :action " +
                    "and p.effect = :effect"
            )
            .setParameter("authorityName", authorityName)
            .setParameter("targetType", TargetType.ENTITY)
            .setParameter("action", action)
            .setParameter("effect", effect)
            .executeUpdate();
    }

    public void deleteSpecificAttributePermissions(String authorityName, String entityPrefix, String wildcardTarget, String action, String effect) {
        entityManager
            .createQuery(
                "delete from SecPermission p " +
                    "where p.authorityName = :authorityName " +
                    "and p.targetType = :targetType " +
                    "and p.target like :entityPrefix " +
                    "and p.target != :wildcardTarget " +
                    "and p.action = :action " +
                    "and p.effect = :effect"
            )
            .setParameter("authorityName", authorityName)
            .setParameter("targetType", TargetType.ATTRIBUTE)
            .setParameter("entityPrefix", entityPrefix)
            .setParameter("wildcardTarget", wildcardTarget)
            .setParameter("action", action)
            .setParameter("effect", effect)
            .executeUpdate();
    }
}
