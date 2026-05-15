package com.vn.security.core.security.store;

import com.vn.security.core.security.domain.SecMenuDefinition;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class SecMenuDefinitionStore {

    private final EntityManager entityManager;

    public SecMenuDefinitionStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public SecMenuDefinition save(SecMenuDefinition entity) {
        if (entity.getId() == null) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    public void deleteById(Long id) {
        SecMenuDefinition entity = entityManager.find(SecMenuDefinition.class, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Transactional(readOnly = true)
    public Optional<SecMenuDefinition> findById(Long id) {
        return Optional.ofNullable(entityManager.find(SecMenuDefinition.class, id));
    }

    @Transactional(readOnly = true)
    public Optional<SecMenuDefinition> findByAppNameAndMenuId(String appName, String menuId) {
        return entityManager
            .createQuery(
                "select d from SecMenuDefinition d where d.appName = :appName and d.menuId = :menuId",
                SecMenuDefinition.class
            )
            .setParameter("appName", appName)
            .setParameter("menuId", menuId)
            .getResultStream()
            .findFirst();
    }

    @Transactional(readOnly = true)
    public List<SecMenuDefinition> findAllByAppNameOrderByOrderingAscIdAsc(String appName) {
        return entityManager
            .createQuery(
                "select d from SecMenuDefinition d where d.appName = :appName order by d.ordering asc, d.id asc",
                SecMenuDefinition.class
            )
            .setParameter("appName", appName)
            .getResultList();
    }

    @Transactional(readOnly = true)
    public List<SecMenuDefinition> findAllByOrderByAppNameAscOrderingAscIdAsc() {
        return entityManager
            .createQuery("select d from SecMenuDefinition d order by d.appName asc, d.ordering asc, d.id asc", SecMenuDefinition.class)
            .getResultList();
    }
}
