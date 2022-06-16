# BrickORM

An ORM library based on [Hibernate](https://hibernate.org/) to be used in Minecraft plugins and extensions.

## API

### Gradle

```
repositories {
    maven { url "https://repo.jorisg.com/snapshots" }
}

dependencies {
    implementation 'com.guflimc.brick.orm:common:+'
}
```

### Usage

Check the [javadocs](https://guflimc.github.io/BrickORM/)

Extend `HibernateDatabaseContext` and add the entity classes.
