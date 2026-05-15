package com.vn.security.core.security.store;

import com.vn.security.core.security.domain.MenuAppName;
import com.vn.security.core.security.domain.SecMenuPermission;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class SecMenuPermissionStore {

    private final EntityManager entityManager;

    public SecMenuPermissionStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public SecMenuPermission save(SecMenuPermission entity) {
        if (entity.getId() == null) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    public void deleteById(Long id) {
        SecMenuPermission entity = entityManager.find(SecMenuPermission.class, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    public void deleteByAppNameAndMenuId(MenuAppName appName, String menuId) {
        entityManager
            .createQuery("delete from SecMenuPermission p where p.appName = :appName and p.menuId = :menuId")
            .setParameter("appName", appName)
            .setParameter("menuId", menuId)
            .executeUpdate();
    }

    @Transactional(readOnly = true)
    public List<SecMenuPermission> findAllByAppNameAndRoleIn(MenuAppName appName, Collection<String> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        return entityManager
            .createQuery(
                "select p from SecMenuPermission p where p.appName = :appName and p.role in :roles",
                SecMenuPermission.class
            )
            .setParameter("appName", appName)
            .setParameter("roles", roles)
            .getResultList();
    }

    @Transactional(readOnly = true)
    public List<SecMenuPermission> findAllByRoleOrderByAppNameAscMenuIdAsc(String role) {
        return entityManager
            .createQuery(
                "select p from SecMenuPermission p where p.role = :role order by p.appName asc, p.menuId asc",
                SecMenuPermission.class
            )
            .setParameter("role", role)
            .getResultList();
    }

    @Transactional(readOnly = true)
    public List<SecMenuPermission> findAllByRoleAndAppNameOrderByMenuIdAsc(String role, MenuAppName appName) {
        return entityManager
            .createQuery(
                "select p from SecMenuPermission p where p.role = :role and p.appName = :appName order by p.menuId asc",
                SecMenuPermission.class
            )
            .setParameter("role", role)
            .setParameter("appName", appName)
            .getResultList();
    }
}
