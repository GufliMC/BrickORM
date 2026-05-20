package com.gufli.brick.orm.api.database;

import io.github.wasabithumb.jtoml.serial.TomlSerializable;

public class DatabaseConfig implements TomlSerializable {

    public String dsn;
    public String driver;
    public String username;
    public String password;

}
