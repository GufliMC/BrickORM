package com.gufli.brick.orm.api.database;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DatabaseContext {

    /**
     * Helper method to find an entity by the given entity class and id async.
     *
     * @param entityType entity class
     * @param id         id of the entity
     * @return a future that will be completed with the entity or null if not found
     */
    <T> CompletableFuture<T> findAsync(Class<T> entityType, Object id);

    /**
     * Helper method to find all entities of a given entity class async.
     *
     * @param entityType entity class
     * @return a future that will be completed with the list of entities
     */
    <T> CompletableFuture<List<T>> findAllAsync(Class<T> entityType);

    /**
     * Helper method to find all entities of a given entity class async with a single where condition.
     *
     * @param entityType entity class
     * @param field      name of the entity field
     * @param value      value of the field
     * @return a future that will be completed with the list of entities
     */
    <T> CompletableFuture<List<T>> findAllWhereAsync(Class<T> entityType, String field, Object value);

    /**
     * Helper method to persist entities async.
     *
     * @param objects entities to persist
     * @return a future that will be completed when this action is done.
     */
    CompletableFuture<Void> persistAsync(Object... objects);

    /**
     * Helper method to persist entities async.
     *
     * @param objects entities to persist
     * @return a future that will be completed when this action is done.
     */
    CompletableFuture<Void> persistAsync(Collection<Object> objects);

    /**
     * Helper method to delete entities async.
     *
     * @param objects entities to delete
     * @return a future that will be completed when this action is done.
     */
    CompletableFuture<Void> removeAsync(Object... objects);

    /**
     * Helper method to delete entities async.
     *
     * @param objects entities to delete
     * @return a future that will be completed when this action is done.
     */
    CompletableFuture<Void> removeAsync(Collection<Object> objects);

}
