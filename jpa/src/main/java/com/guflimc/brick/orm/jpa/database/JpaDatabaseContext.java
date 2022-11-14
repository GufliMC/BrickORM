package com.guflimc.brick.orm.jpa.database;

import com.guflimc.brick.orm.api.database.DatabaseContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public interface JpaDatabaseContext extends DatabaseContext {

    /**
     * Helper method to merge a detached entity into a managed entity and flush async.
     *
     * @param object entity to update
     * @return a future that will be completed when this action is done and returns the managed entity.
     */
    <T> CompletableFuture<T> mergeAsync(T object);

    /**
     * Helper method to build and execute a query for a new session async.
     *
     * @param consumer consumer that will build and execute the query
     * @return a future that will be completed when the query is done
     */
    CompletableFuture<Void> queryBuilderAsync(BiConsumer<EntityManager, CriteriaBuilder> consumer);

    /**
     * Helper method to build and execute a query for a new session async.
     *
     * @param func function that will build and execute the query and return the result
     * @return a future that will be completed with the result when the query is done
     */
    <T> CompletableFuture<T> queryBuilderAsync(BiFunction<EntityManager, CriteriaBuilder, T> func);

    /**
     * Helper method to find all entities of a given entity class async with the ability to refine the selection.
     *
     * @param entityType entity class
     * @param modifier   function that will modify the query
     * @return a future that will be completed with the list of entities
     */
    <T> CompletableFuture<List<T>> findAllAsync(Class<T> entityType, FindAllModifier<T> modifier);

    /**
     * Helper method to find all entities of a given entity class async with a where clause.
     *
     * @param entityType entity class
     * @param modifier   function that create the where clause
     * @return a future that will be completed with the list of entities
     */
    <T> CompletableFuture<List<T>> findAllWhereAsync(Class<T> entityType, BiFunction<CriteriaBuilder, Root<T>, Predicate> modifier);

    /**
     * Functional interface to apply filters on a query.
     */
    @FunctionalInterface
    interface FindAllModifier<T> {
        CriteriaQuery<T> modify(CriteriaBuilder builder, Root<T> root, CriteriaQuery<T> query);
    }

}
