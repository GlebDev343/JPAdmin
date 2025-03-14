# JPAdmin

JPAdmin is an admin panel for Java applications using JPA, similar to Rails Active Admin or Django Admin. It provides a way to manage database entities dynamically with minimal configuration.

## Installation

To use JPAdmin in your project, you need to integrate it into your local Maven repository. Follow these steps:

1. Clone the repository:
   ```sh
   git clone https://github.com/GlebDev343/JPAdmin.git
   cd JPAdmin
   ```
2. Build and install the project locally:
   ```sh
   mvn clean install -DskipTests
   ```
3. Add JPAdmin as a dependency in your `pom.xml`:
   ```xml
   <dependency>
       <groupId>by.glebka</groupId>
       <artifactId>jpadmin</artifactId>
       <version>0.2.0</version>
   </dependency>
   ```

## Integration

To enable JPAdmin in your Spring Boot application, import the `JpaAdminAutoConfiguration` class:

```java
import by.glebka.jpadmin.config.JpaAdminAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(JpaAdminAutoConfiguration.class)
public class ExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
```

## Configuration

JPAdmin provides two main configuration options to customize its behavior. These can be defined in either `application.yml` or `application.properties`:

- `jpadmin.base-package`: Specifies the root package where JPAdmin will scan for JPA entities to include in the admin interface. For example, setting it to `com.example.project.entity` limits entity scanning to that package and its subpackages. If not set, JPAdmin scans all packages in your application.
- `jpadmin.enabled`: Controls whether the JPAdmin interface is active. Set to `true` (default) to enable the admin panel, or `false` to disable it entirely, preventing JPAdmin from initializing.
### Configuration in `application.yml`

```yaml
jpadmin:
  base-package: com.example.project.entity
  enabled: true
```

### Configuration in `application.properties`

```properties
jpadmin.base-package=com.example.project.entity
jpadmin.enabled=true
```

## Entity Mapping

JPAdmin automatically maps standard SQL types using `columnDefinition`. For custom types, you need to extend Hibernate's `UserType`.

### Standard and Custom Column Mapping

```java
import by.glebka.jpadmin.type.IntervalStringType;
import org.hibernate.annotations.Type;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Product {
    @Id
    @Column(name = "id", columnDefinition = "INTEGER")
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "interval_col")
    @Type(IntervalStringType.class)
    private String intervalCol;
}
```

### Available Custom Types

JPAdmin provides several custom Hibernate `UserType` implementations for handling non-standard database types:

- `BitStringType` → BIT(n)
- `BitVaryingType`→ BIT VARYING(n)
- `BoxType` → BOX
- `CidrType` → CIDR
- `CircleType` → CIRCLE
- `InetType` → INET
- `IntervalStringType` → INTERVAL
- `LineStringType` → LINE
- `JsonBinaryType` → JSONB
- `JsonStringType` → JSON
- `LsegStringType` → LSEG
- `MacaddrStringType` → MACADDR
- `OffsetTimeType` → TIMETZ
- `PathStringType` → PATH
- `PgLsnStringType` → PG_LSN
- `PointStringType` → POINT
- `PolygonStringType` → POLYGON
- `TsqueryStringType` → TSQUERY
- `TsvectorStringType` → TSVECTOR
- `TxidSnapshotStringType` → TXID_SNAPSHOT

## Customization

By default, JPAdmin displays all database tables and columns, but you can customize the behavior in the following ways:

### Customization via Configuration

To customize tables and columns programmatically, register them in a Spring configuration class:

```java
import by.glebka.jpadmin.config.AdminConfig;
import by.glebka.jpadmin.config.TableConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductAdminConfig {
    @Bean
    public AdminConfig adminConfig() {
        AdminConfig config = new AdminConfig();
        config.registerTable(Product.class, new TableConfig(Product.class)
                .setDisplayName("Products")
                .addColumn("id", "ID")
                .addColumn("name", "Product Name")
                .addColumn("intervalCol", "Interval Column")
                .addComputedColumn("availability", "Availability", product ->
                        ((Product) product).getIntervalCol() != null ? "Defined" : "Undefined")
                .setDefaultSortField("name")
                .setDefaultSortOrder("ASC"));
        return config;
    }
}
```

### Explanation of TableConfig Methods

- `setDisplayName(String name)` – Sets a custom display name for the table.
- `addColumn(String fieldName, String displayName)` – Maps a database column to a human-readable name.
- `addComputedColumn(String name, String displayName, Function<Object, String> function)` – Adds a computed column that derives its value from other fields.
- `setDefaultSortField(String fieldName)` – Defines which column is used for default sorting.
- `setDefaultSortOrder(String order)` – Specifies the sorting order (`ASC` for ascending, `DESC` for descending).
- `setDefaultNullsFirst(boolean nullsFirst)` – Determines whether `NULL` values appear first in sorting.

### Customization via Entity Annotation

Alternatively, you can define computed columns directly within the entity class:

```java
import by.glebka.jpadmin.annotation.ComputedColumn;

public class Product {
    private String name;
    private Integer stock;

    @ComputedColumn(displayName = "Stock Status")
    public String getStockStatus() {
        return stock != null && stock > 0 ? "In Stock" : "Out of Stock";
    }
}
```
