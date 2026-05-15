package com.vn.security.core.security.fetch;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Subgraph;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for fetch-plan metadata operations.
 * Provides helpers to inspect entity properties, identify JPA entity types,
 * and convert a {@link FetchPlan} into a JPA {@link EntityGraph}.
 */
public final class FetchPlanMetadataTools {

    private FetchPlanMetadataTools() {
        // utility class
    }

    /**
     * Returns the JavaBean property names for the given class, excluding {@code class}.
     *
     * @param entityClass the class to inspect
     * @return list of property names
     */
    public static List<String> getPropertyNames(Class<?> entityClass) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(entityClass);
            return Arrays.stream(beanInfo.getPropertyDescriptors())
                .map(PropertyDescriptor::getName)
                .filter(name -> !"class".equals(name))
                .toList();
        } catch (IntrospectionException e) {
            throw new IllegalArgumentException("Cannot introspect entity class: " + entityClass.getName(), e);
        }
    }

    /**
     * Returns {@code true} if the given type is annotated with {@code @Entity}.
     *
     * @param type the class to test
     * @return true if it is a JPA entity
     */
    public static boolean isEntityType(Class<?> type) {
        return type.isAnnotationPresent(Entity.class);
    }

    /**
     * Converts a {@link FetchPlan} into a JPA {@link EntityGraph} so it can be applied
     * to a {@link jakarta.persistence.TypedQuery} via the {@code jakarta.persistence.fetchgraph} hint.
     * Nested associations with sub-plans are added recursively as {@link Subgraph} nodes.
     */
    public static <T> EntityGraph<T> toEntityGraph(EntityManager entityManager, Class<T> entityClass, FetchPlan plan) {
        EntityGraph<T> graph = entityManager.createEntityGraph(entityClass);
        applyToGraph(graph, plan.getProperties());
        return graph;
    }

    private static void applyToGraph(EntityGraph<?> graph, List<FetchPlanProperty> properties) {
        for (FetchPlanProperty property : properties) {
            if (property.fetchPlan() == null) {
                graph.addAttributeNodes(property.name());
            } else {
                Subgraph<?> subgraph = graph.addSubgraph(property.name());
                applyToSubgraph(subgraph, property.fetchPlan().getProperties());
            }
        }
    }

    private static void applyToSubgraph(Subgraph<?> subgraph, List<FetchPlanProperty> properties) {
        for (FetchPlanProperty property : properties) {
            if (property.fetchPlan() == null) {
                subgraph.addAttributeNodes(property.name());
            } else {
                Subgraph<?> nested = subgraph.addSubgraph(property.name());
                applyToSubgraph(nested, property.fetchPlan().getProperties());
            }
        }
    }
}
