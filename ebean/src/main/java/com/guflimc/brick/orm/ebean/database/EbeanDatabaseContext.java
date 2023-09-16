package com.guflimc.brick.orm.ebean.database;

import com.guflimc.brick.orm.api.database.DatabaseContext;
import io.ebean.Database;
import io.ebean.DatabaseFactory;
import io.ebean.Transaction;
import io.ebean.config.DatabaseConfig;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import io.ebean.migration.MigrationConfig;
import io.ebean.migration.MigrationRunner;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class EbeanDatabaseContext implements DatabaseContext {

    private final String dataSourceName;

    private final Database database;

    public EbeanDatabaseContext(EbeanConfig config, String dataSourceName) {
        this(config, dataSourceName, 15);
    }

    public EbeanDatabaseContext(EbeanConfig config, String dataSourceName, int poolSize) {
        this.dataSourceName = dataSourceName;

        // change context classloader
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

        // datasource config
        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        dataSourceConfig.setUrl(config.dsn);
        dataSourceConfig.setUsername(config.username);
        dataSourceConfig.setPassword(config.password);

        if (config.driver != null) {
            dataSourceConfig.setDriver(config.driver);
        }

        // initialize pool
        DataSourcePool pool = DataSourceFactory.create(dataSourceName, dataSourceConfig);
        pool.setMaxSize(poolSize);

        // initialize database
        DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setAllQuotedIdentifiers(true);
        databaseConfig.setDisableLazyLoading(true);
        databaseConfig.setDataSource(pool);
        databaseConfig.setRegister(true);
        databaseConfig.setDefaultServer(false);
        databaseConfig.setName(dataSourceName);

        // register classes
        Arrays.stream(applicableClasses()).forEach(databaseConfig::addClass);

        database = DatabaseFactory.create(databaseConfig);

        // migrate
        migrate();

        // set context class loader
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    public final void shutdown() {
        if (database != null) {
            database.shutdown();
        }
    }

    private void migrate() {
        MigrationConfig config = new MigrationConfig();
        DataSource ds = database.dataSource();

        try {
            Connection conn = ds.getConnection();
            String platform = conn.getMetaData().getDatabaseProductName().toLowerCase();
            config.setMigrationPath("dbmigrations/" + platform);

            MigrationRunner runner = new MigrationRunner(config);
            runner.run(ds);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Must be overriden, used to specify all classes for this database context.
     *
     * @return array of classes
     */
    protected abstract Class<?>[] applicableClasses();

    //

    private <T> CompletableFuture<T> async(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier)
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }

    private CompletableFuture<Void> async(Runnable runnable) {
        return async(() -> {
            runnable.run();
            return null;
        });
    }

    //

    public CompletableFuture<Void> transactionAsync(Consumer<Transaction> consumer) {
        return async(() -> {
            try (Transaction transaction = database.beginTransaction();) {
                consumer.accept(transaction);
                transaction.commit();
            }
        });
    }

    //

    @Override
    public final CompletableFuture<Void> persistAsync(Object... objects) {
        return transactionAsync(t -> database.saveAll(objects));
    }

    @Override
    public CompletableFuture<Void> persistAsync(Collection<Object> objects) {
        return transactionAsync(t -> database.saveAll(objects));
    }

    @Override
    public final CompletableFuture<Void> removeAsync(Object... objects) {
        return removeAsync(List.of(objects));
    }

    @Override
    public final CompletableFuture<Void> removeAsync(Collection<Object> objects) {
        return transactionAsync(t -> database.deleteAll(objects));
    }

    //


    @Override
    public <T> CompletableFuture<T> findAsync(Class<T> entityType, Object id) {
        return async(() -> database.createQuery(entityType).setId(id).findOne());
    }

    @Override
    public <T> CompletableFuture<List<T>> findAllAsync(Class<T> entityType) {
        return async(() -> database.createQuery(entityType).findList());
    }

    @Override
    public <T> CompletableFuture<List<T>> findAllWhereAsync(Class<T> entityType, String field, Object value) {
        return async(() -> database.createQuery(entityType).where().eq(field, value).findList());
    }

    //

    public CompletableFuture<Void> refreshAsync(Object bean) {
        return async(() -> {
            database.refresh(bean);
            Arrays.stream(bean.getClass().getDeclaredFields())
                    .filter(f -> f.isAnnotationPresent(OneToMany.class) || f.isAnnotationPresent(ManyToMany.class))
                    .forEach(f -> database.refreshMany(bean, f.getName()));
        });
    }
}
