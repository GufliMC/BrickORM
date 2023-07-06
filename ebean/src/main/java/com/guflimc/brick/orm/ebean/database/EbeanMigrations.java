package com.guflimc.brick.orm.ebean.database;

import io.ebean.Database;
import io.ebean.DatabaseFactory;
import io.ebean.annotation.Platform;
import io.ebean.config.DatabaseConfig;
import io.ebean.config.dbplatform.DatabasePlatform;
import io.ebean.config.dbplatform.DatabasePlatformProvider;
import io.ebean.datasource.DataSourceConfig;
import io.ebean.dbmigration.DbMigration;
import io.ebean.migration.MigrationConfig;
import io.ebean.migration.MigrationRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EbeanMigrations {

    private final DbMigration dbMigration;
    private final String dataSourceName;

    private final List<Class<?>> classes = new ArrayList<>();

    public EbeanMigrations(String dataSourceName, Path resourcePath, Platform... platforms) {
        this.dataSourceName = dataSourceName;

        this.dbMigration = DbMigration.create();
        this.dbMigration.setPathToResources(resourcePath.toString());
        this.dbMigration.setMigrationPath("dbmigrations");

        for (Platform platform : platforms) {
            this.dbMigration.addPlatform(platform);
        }
    }

    public void addClass(Class<?> clazz) {
        classes.add(clazz);
    }

    public void generate() throws IOException, SQLException {
        generate(false);
    }

    public void generate(boolean strictMode) throws IOException, SQLException {
        // create mock db with same name as used in the app
        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        dataSourceConfig.setUrl("jdbc:h2:mem:migrationdb;");
        dataSourceConfig.setUsername("dbuser");
        dataSourceConfig.setPassword("");

        // use same datasource name
        DatabaseConfig config = new DatabaseConfig();
        config.setDatabasePlatform(new DatabasePlatform());
        config.setDataSourceConfig(dataSourceConfig);
        config.setName(dataSourceName);
        config.setAllQuotedIdentifiers(true);
        classes.forEach(config::addClass);

        // create database
        Database database = DatabaseFactory.create(config);
        dbMigration.setServer(database);
        dbMigration.setStrictMode(strictMode);

        // generate migrations
        dbMigration.generateMigration();

        // run migrations to verify them on mock database
        MigrationConfig migrationConfig = new MigrationConfig();
        Connection conn = database.dataSource().getConnection();
        migrationConfig.setMigrationPath("dbmigrations/h2");

        MigrationRunner runner = new MigrationRunner(migrationConfig);
        runner.run(conn);
    }

}
