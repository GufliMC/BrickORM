package com.guflimc.brick.orm.database;

public class HibernateConfig {

    public final String dsn;
    public final String driver;
    public final String username;
    public final String password;

    public final boolean debug;

    public HibernateConfig(String dsn, String driver, String username, String password, boolean debug) {
        this.dsn = dsn;
        this.driver = driver;
        this.username = username;
        this.password = password;
        this.debug = debug;
    }

    public HibernateConfig(String dsn, String driver, String username, String password) {
        this(dsn, driver, username, password, false);
    }

}
