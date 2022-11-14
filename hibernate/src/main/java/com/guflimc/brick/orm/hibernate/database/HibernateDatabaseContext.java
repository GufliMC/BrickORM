package com.guflimc.brick.orm.hibernate.database;

import com.guflimc.brick.orm.jpa.database.JpaDatabaseContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.cfg.Configuration;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class HibernateDatabaseContext implements JpaDatabaseContext {

    private final SessionFactory sessionFactory;

    public HibernateDatabaseContext(HibernateConfig config) {
        this(config, 15);
    }

    public HibernateDatabaseContext(HibernateConfig config, int poolSize) {
        Properties properties = new Properties();

        Configuration configuration = new Configuration();
        configuration.setProperties(properties);
        configuration.setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy());

        properties.setProperty("hibernate.connection.url", config.dsn);
        properties.setProperty("hibernate.connection.username", config.username);
        properties.setProperty("hibernate.connection.password", config.password);

        properties.setProperty("hibernate.connection.pool_size", poolSize + "");
        properties.setProperty("hibernate.hbm2ddl.auto", "update");

        if (config.driver != null) {
            properties.setProperty("hibernate.connection.driver_class", config.driver);
        }

        if (config.dialect != null) {
            properties.setProperty("hibernate.dialect", config.dialect);
        }

        if (config.debug) {
            properties.setProperty("hibernate.show_sql", "true");
            properties.setProperty("hibernate.generate_statistics", "true");
        }

        if (!config.disableL2C) {
            properties.setProperty("hibernate.cache.use_second_level_cache", "true");
            properties.setProperty("hibernate.cache.region.factory_class", "org.hibernate.cache.jcache.JCacheRegionFactory");
            properties.setProperty("hibernate.javax.cache.provider", "org.ehcache.jsr107.EhcacheCachingProvider");
            properties.setProperty("hibernate.javax.cache.uri", "classpath:ehcache.xml");
            configuration.setSharedCacheMode(SharedCacheMode.DISABLE_SELECTIVE);
        }

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
     * Print a summary of the main statistics at INFO log level.
     */
    public final void printStatistics() {
        sessionFactory.getStatistics().logSummary();
    }

    //

    private <T> CompletableFuture<T> async(Function<EntityManager, T> func) {
        return CompletableFuture.supplyAsync(() -> {
            try (Session session = sessionFactory.openSession()) {
                return func.apply(session);
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    private CompletableFuture<Void> transactionAsync(Consumer<EntityManager> consumer) {
        return async(em -> {
            em.getTransaction().begin();
            consumer.accept(em);
            em.getTransaction().commit();
            return null;
        });
    }

    //

    @Override
    public final CompletableFuture<Void> persistAsync(Object... objects) {
        return transactionAsync(em -> {
            Arrays.stream(objects).forEach(em::persist);
        });
    }

    @Override
    public CompletableFuture<Void> persistAsync(Collection<Object> objects) {
        return transactionAsync(em -> {
            objects.forEach(em::persist);
        });
    }

    @Override
    public final CompletableFuture<Void> removeAsync(Object... object) {
        return transactionAsync((em -> {
            Arrays.stream(object).forEach(em::remove);
        }));
    }

    @Override
    public CompletableFuture<Void> removeAsync(Collection<Object> objects) {
        return transactionAsync(em -> {
            objects.forEach(em::remove);
        });
    }

    @Override
    public final <T> CompletableFuture<T> mergeAsync(T object) {
        CompletableFuture<T> future = new CompletableFuture<>();
        AtomicReference<T> ref = new AtomicReference<>();
        transactionAsync((em -> {
            ref.set(em.merge(object));
        })).thenRun(() -> {
            future.complete(ref.get());
        });
        return future;
    }

//    @Override
//    public final <T> CompletableFuture<T> updateAsync(T object) {
//        return mergeAsync(object).thenApply((managed) -> {
//            try {
//                copyAvailableFields(managed, object);
//            } catch (IllegalAccessException e) {
//                throw new RuntimeException(e);
//            }
//            return managed;
//        });
//    }
//
//    private static <T> void copyAvailableFields(@NotNull T source, @NotNull T target) throws IllegalAccessException {
//        Field[] fields = source.getClass().getDeclaredFields();
//        for (Field field : fields) {
//            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
//                    && !Modifier.isTransient(field.getModifiers())) {
//                System.out.println(field.getName());
//                field.setAccessible(true);
//                field.set(target, field.get(source));
//            }
//        }
//    }

    @Override
    public final CompletableFuture<Void> queryBuilderAsync(BiConsumer<EntityManager, CriteriaBuilder> consumer) {
        return async(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            consumer.accept(em, cb);
            return null;
        });
    }

    @Override
    public final <T> CompletableFuture<T> queryBuilderAsync(BiFunction<EntityManager, CriteriaBuilder, T> func) {
        return async(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            return func.apply(em, cb);
        });
    }

    @Override
    public final <T> CompletableFuture<T> findAsync(Class<T> entityType, Object id) {
        return async(em -> em.find(entityType, id));
    }

    @Override
    public final <T> CompletableFuture<List<T>> findAllAsync(Class<T> entityType) {
        return findAllAsync(entityType, (cb, root, cq) -> cq);
    }

    @Override
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

    @Override
    public final <T> CompletableFuture<List<T>> findAllWhereAsync(Class<T> entityType, BiFunction<CriteriaBuilder, Root<T>, Predicate> modifier) {
        return findAllAsync(entityType, (cb, root, cq) -> {
            cq = cq.where(modifier.apply(cb, root));
            return cq;
        });
    }

    @Override
    public final <T> CompletableFuture<List<T>> findAllWhereAsync(Class<T> entityType, String field, Object value) {
        return findAllWhereAsync(entityType, (cb, root) -> cb.equal(root.get(field), value));
    }

}
