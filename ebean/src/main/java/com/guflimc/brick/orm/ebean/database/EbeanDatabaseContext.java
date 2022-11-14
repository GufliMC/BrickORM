package com.guflimc.brick.orm.ebean.database;

import com.guflimc.brick.orm.api.database.DatabaseContext;
import io.ebean.DB;
import io.ebean.Database;
import io.ebean.DatabaseFactory;
import io.ebean.Transaction;
import io.ebean.config.DatabaseConfig;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.datasource.DataSourceFactory;
import io.ebean.datasource.DataSourcePool;
import io.ebean.migration.MigrationConfig;
import io.ebean.migration.MigrationRunner;
import io.ebean.typequery.TQRootBean;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class EbeanDatabaseContext implements DatabaseContext {

    private final DataSourcePool pool;
    private final String dataSourceName;

    private final Database database;

    public EbeanDatabaseContext(EbeanConfig config, String dataSourceName) {
        this(config, dataSourceName,15);
    }

    public EbeanDatabaseContext(EbeanConfig config, String dataSourceName, int poolSize) {
        this.dataSourceName = dataSourceName;

        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        dataSourceConfig.setUrl(config.dsn);
        dataSourceConfig.setUsername(config.username);
        dataSourceConfig.setPassword(config.password);

        if (config.driver != null) {
            dataSourceConfig.setDriver(config.driver);
        }

        pool = DataSourceFactory.create(dataSourceName, dataSourceConfig);
        pool.setMaxSize(poolSize);

        migrate(pool);
        database = connect(pool);
    }

    private void migrate(DataSourcePool pool) {
        MigrationConfig config = new MigrationConfig();

        try {
            Connection conn = pool.getConnection();
            String platform = conn.getMetaData().getDatabaseProductName().toLowerCase();
            config.setMigrationPath("dbmigrations/" + platform);

            MigrationRunner runner = new MigrationRunner(config);
            runner.run(conn);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Database connect(DataSourcePool pool) {
        DatabaseConfig config = new DatabaseConfig();
        config.setDataSource(pool);
        config.setRegister(true);
        config.setDefaultServer(false);
        config.setName(dataSourceName);

        // register classes
        Arrays.stream(entityClasses()).forEach(config::addClass);

        return DatabaseFactory.create(config);
    }

    public final void shutdown() {
        if (pool != null) {
            pool.shutdown();
        }
    }

    /**
     * Must be overriden, used to specify all entity classes for this database context.
     *
     * @return array of entity classes
     */
    protected abstract Class<?>[] entityClasses();

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
}
