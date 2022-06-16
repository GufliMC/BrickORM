package com.guflimc.brick.orm.database;

import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class HibernateDatabaseContext {

    private final SessionFactory sessionFactory;

    public HibernateDatabaseContext(HibernateConfig config) {
        this(config, 5);
    }

    public HibernateDatabaseContext(HibernateConfig config, int poolSize) {
        Properties properties = new Properties();
        properties.setProperty("hibernate.connection.url", config.dsn);
        properties.setProperty("hibernate.connection.username", config.username);
        properties.setProperty("hibernate.connection.password", config.password);

        if (config.driver != null) {
            properties.setProperty("hibernate.connection.driver_class", config.driver);
        }

        properties.setProperty("hibernate.connection.pool_size", poolSize + "");
        properties.setProperty("hibernate.hbm2ddl.auto", "update");

        Configuration configuration = new Configuration();
        configuration.setProperties(properties);

        // register classes
        Arrays.stream(entityClasses()).forEach(configuration::addAnnotatedClass);

        this.sessionFactory = configuration.buildSessionFactory();
    }

    /**
     * Must be overriden, used to specify all entity classes for this database context.
     * @return array of entity classes
     */
    protected abstract Class<?>[] entityClasses();

    /**
     * Retrieve the session factory for this database context.
     * @return session factory
     */
    public final SessionFactory sessionFactory() {
        return sessionFactory;
    }

    /**
     * Helper method to execute async statements on a new session.
     * @param consumer consumer that will execute statements
     * @return future that will be completed when the consumer is done
     */
    public final CompletableFuture<Void> async(Consumer<Session> consumer) {
        return CompletableFuture.runAsync(() -> {
            try (
                    Session session = sessionFactory.openSession();
            ) {
                Transaction tx = session.beginTransaction();
                consumer.accept(session);
                tx.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    /**
     * Helper method to find an entity by the given entity class and id async.
     * @param entityType entity class
     * @param id id of the entity
     * @return a future that will be completed with the entity or null if not found
     */
    public final <T> CompletableFuture<T> findAsync(Class<T> entityType, Object id) {
        return CompletableFuture.supplyAsync(() -> {
            try (
                    Session session = sessionFactory.getCurrentSession();
            ) {
                return session.find(entityType, id);
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    /**
     * Helper method to find all entities of a given entity class async.
     * @param entityType entity class
     * @return a future that will be completed with the list of entities
     */
    public final <T> CompletableFuture<List<T>> findAllAsync(Class<T> entityType) {
        return findAllAsync(entityType, Function.identity());
    }

    /**
     * Helper method to find all entities of a given entity class async with the ability to refine the selection.
     * @param entityType entity class
     * @param modifier function that will modify the query
     * @return a future that will be completed with the list of entities
     */
    public final <T> CompletableFuture<List<T>> findAllAsync(Class<T> entityType, Function<CriteriaQuery<T>, CriteriaQuery<T>> modifier) {
        return CompletableFuture.supplyAsync(() ->
                queryBuilder((session, cb) -> {
                    CriteriaQuery<T> query = cb.createQuery(entityType);
                    Root<T> root = query.from(entityType);
                    query = modifier.apply(query);
                    query = query.select(root);

                    TypedQuery<T> typedQuery = session.createQuery(query);
                    return typedQuery.getResultList();
                }));
    }

    /**
     * Helper method to insert an entity into the database async.
     * @param object entity to insert
     * @return a future that will be completed when this action is done.
     */
    public final CompletableFuture<Void> persistAsync(Object object) {
        return async((session -> session.persist(object)));
    }

    /**
     * Helper method to update an entity in the database async.
     * @param object entity to update
     * @return a future that will be completed when this action is done.
     */
    public final CompletableFuture<Void> mergeAsync(Object object) {
        return async((session -> session.merge(object)));
    }

    /**
     * Helper method to delete an entity from the database async.
     * @param object entity to delete
     * @return a future that will be completed when this action is done.
     */
    public final CompletableFuture<Void> removeAsync(Object object) {
        return async((session -> session.remove(object)));
    }

    /**
     * Helper method to build and execute a query for a new session.
     * @param consumer consumer that will build and execute the query
     */
    public final void queryBuilder(BiConsumer<Session, CriteriaBuilder> consumer) {
        try (
                Session session = sessionFactory.openSession();
        ) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            consumer.accept(session, cb);
        }
    }

    /**
     * Helper method to build and execute a query for a new session.
     * @param func function that will build and execute the query and return the result.
     * @return result of the query
     */
    public final <T> T queryBuilder(BiFunction<Session, CriteriaBuilder, T> func) {
        try (
                Session session = sessionFactory.openSession();
        ) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            return func.apply(session, cb);
        }
    }

    /**
     * Helper method to build and execute a query for a new session async.
     * @param consumer consumer that will build and execute the query
     * @return a future that will be completed when the query is done
     */
    public final CompletableFuture<Void> queryBuilderAsync(BiConsumer<Session, CriteriaBuilder> consumer) {
        return CompletableFuture.runAsync(() -> queryBuilder(consumer));
    }

    /**
     * Helper method to build and execute a query for a new session async.
     * @param func function that will build and execute the query and return the result
     * @return a future that will be completed with the result when the query is done
     */
    public final <T> CompletableFuture<T> queryBuilderAsync(BiFunction<Session, CriteriaBuilder, T> func) {
        return CompletableFuture.supplyAsync(() -> queryBuilder(func));
    }

}
