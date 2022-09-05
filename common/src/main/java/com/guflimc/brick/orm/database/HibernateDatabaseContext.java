package com.guflimc.brick.orm.database;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
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
//        properties.setProperty("hibernate.current_session_context_class", "thread");

        Configuration configuration = new Configuration();
        configuration.setProperties(properties);

        // register classes
        Arrays.stream(entityClasses()).forEach(configuration::addAnnotatedClass);

        this.sessionFactory = configuration.buildSessionFactory();
    }

    /**
     * Must be overriden, used to specify all entity classes for this database context.
     *
     * @return array of entity classes
     */
    protected abstract Class<?>[] entityClasses();

    /**
     * Helper method to execute async operations on a new session.
     *
     * @param func function that will execute statements and return a value
     * @return future that will be completed with the return value when done
     */
    public final <T> CompletableFuture<T> async(Function<EntityManager, T> func) {
        return CompletableFuture.supplyAsync(() -> {
            try (Session session = sessionFactory.openSession()) {
                return func.apply(session);
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    /**
     * Helper method to execute an async transaction.
     *
     * @param consumer consumer that will execute statements
     * @return future that will be completed when the transaction is done.
     */
    public final CompletableFuture<Void> transactionAsync(Consumer<EntityManager> consumer) {
        return async(em -> {
            em.getTransaction().begin();
            consumer.accept(em);
            em.getTransaction().commit();
            return null;
        });
    }

    /**
     * Helper method to find an entity by the given entity class and id async.
     *
     * @param entityType entity class
     * @param id         id of the entity
     * @return a future that will be completed with the entity or null if not found
     */
    public final <T> CompletableFuture<T> findAsync(Class<T> entityType, Object id) {
        return async(em -> em.find(entityType, id));
    }

    /**
     * Helper method to find all entities of a given entity class async.
     *
     * @param entityType entity class
     * @return a future that will be completed with the list of entities
     */
    public final <T> CompletableFuture<List<T>> findAllAsync(Class<T> entityType) {
        return findAllAsync(entityType, (cb, root, cq) -> cq);
    }

    @FunctionalInterface
    public interface FindAllModifier<T> {
        CriteriaQuery<T> modify(CriteriaBuilder builder, Root<T> root, CriteriaQuery<T> query);
    }

    /**
     * Helper method to find all entities of a given entity class async with the ability to refine the selection.
     *
     * @param entityType entity class
     * @param modifier   function that will modify the query
     * @return a future that will be completed with the list of entities
     */
    public final <T> CompletableFuture<List<T>> findAllAsync(Class<T> entityType, FindAllModifier<T> modifier) {
        return queryBuilderAsync((em, cb) -> {
            CriteriaQuery<T> query = cb.createQuery(entityType);
            Root<T> root = query.from(entityType);
            query = modifier.modify(cb, root, query);
            query = query.select(root);

            TypedQuery<T> typedQuery = em.createQuery(query);
            return typedQuery.getResultList();
        });
    }

    /**
     * Helper method to find all entities of a given entity class async with a where clause.
     *
     * @param entityType entity class
     * @param modifier   function that create the where clause
     * @return a future that will be completed with the list of entities
     */
    public final <T> CompletableFuture<List<T>> findAllWhereAsync(Class<T> entityType, BiFunction<CriteriaBuilder, Root<T>, Predicate> modifier) {
        return findAllAsync(entityType, (cb, root, cq) -> {
            cq = cq.where(modifier.apply(cb, root));
            return cq;
        });
    }

    /**
     * Helper method to find all entities of a given entity class async with a single where condition.
     *
     * @param entityType entity class
     * @param field      name of the entity field
     * @param value      value of the field
     * @return a future that will be completed with the list of entities
     */
    public final <T> CompletableFuture<List<T>> findAllWhereAsync(Class<T> entityType, String field, Object value) {
        return findAllWhereAsync(entityType, (cb, root) -> cb.equal(root.get(field), value));
    }

    /**
     * Helper method to insert an entity into the database async.
     *
     * @param object entity to insert
     * @return a future that will be completed when this action is done.
     */
    public final CompletableFuture<Void> persistAsync(Object object) {
        return transactionAsync((em -> {
            em.persist(object);
        }));
    }

    /**
     * Helper method to update an entity in the database async.
     *
     * @param object entity to update
     * @return a future that will be completed when this action is done.
     */
    public final <T> CompletableFuture<T> mergeAsync(T object) {
        CompletableFuture<T> future = new CompletableFuture<>();
        AtomicReference<T> ref = new AtomicReference<>();
        transactionAsync((em -> ref.set(em.merge(object))))
                .thenRun(() -> future.complete(ref.get()));
        return future;
    }

    /**
     * Helper method to delete an entity from the database async.
     *
     * @param object entity to delete
     * @return a future that will be completed when this action is done.
     */
    public final CompletableFuture<Void> removeAsync(Object object) {
        return transactionAsync((em -> {
            em.remove(object);
        }));
    }

    /**
     * Helper method to build and execute a query for a new session async.
     *
     * @param consumer consumer that will build and execute the query
     * @return a future that will be completed when the query is done
     */
    public final CompletableFuture<Void> queryBuilderAsync(BiConsumer<EntityManager, CriteriaBuilder> consumer) {
        return async(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            consumer.accept(em, cb);
            return null;
        });
    }

    /**
     * Helper method to build and execute a query for a new session async.
     *
     * @param func function that will build and execute the query and return the result
     * @return a future that will be completed with the result when the query is done
     */
    public final <T> CompletableFuture<T> queryBuilderAsync(BiFunction<EntityManager, CriteriaBuilder, T> func) {
        return async(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            return func.apply(em, cb);
        });
    }

}
