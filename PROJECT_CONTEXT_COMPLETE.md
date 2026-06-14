# HMWSSB Works Measurement and Estimation System - Complete Codebase Context

This document contains 100% of the project context, database design, backend Java structures, frontend AngularJS controllers, CSS styling, location hierarchy structures, and the complete source code for all project files. Copying this single document into ChatGPT/Gemini/Claude will enable it to assist in development, bug fixes, feature expansion, or general queries with full knowledge of the workspace.

---

## 1. Directory Structure

```text
E:\works\
│   .gitattributes
│   .gitignore
│   clean_and_copy_json.ps1
│   codebase_diagrams.md
│   ESCALATION_NEW_ZONE_CIRCLE.xls
│   HELP.md
│   pom.xml
│   read_sample_users.ps1
│   read_schema_oledb.ps1
│   read_xls.ps1
│   read_xls_oledb.ps1
│   sample users.xlsx
│   users_parsed.json
│
├───.mvn/
│   └───wrapper/
│           maven-wrapper.properties
│
└───src/
    ├───main/
    │   ├───java/
    │   │   └───com/
    │   │       └───hmwssb/
    │   │           └───works/
    │   │               │   WorksApplication.java
    │   │               │
    │   │               ├───controller/
    │   │               │       EstimateController.java
    │   │               │       HomeController.java
    │   │               │       ItemController.java
    │   │               │       UserController.java
    │   │               │
    │   │               ├───model/
    │   │               │       Estimate.java
    │   │               │       EstimateItem.java
    │   │               │       Item.java
    │   │               │       User.java
    │   │               │       UserLocation.java
    │   │               │
    │   │               ├───repository/
    │   │               │       EstimateRepository.java
    │   │               │       ItemRepository.java
    │   │               │       UserRepository.java
    │   │               │
    │   │               └───seeder/
    │   │                       DatabaseSeeder.java
    │   │
    │   └───resources/
    │       │   application.properties
    │       │   users_parsed.json
    │       │
    │       └───static/
    │           │   abstract.html
    │           │   app.js
    │           │   config.js
    │           │   hierarchy.js
    │           │   hierarchy.json
    │           │   index.html
    │           │   login.html
    │           │
    │           └───styles/
    │                   theme.css
    │
    └───test/
        └───java/
            └───com/
                └───hmwssb/
                    └───works/
                            WorksApplicationTests.java
```

---

## 2. High-Level Architecture Overview

The system is a **monolithic client-server web application** built for the **Hyderabad Metropolitan Water Supply and Sewerage Board (HMWSSB)** to measure works and generate estimates.

- **Frontend**: A Single Page Application (SPA) utilizing **AngularJS 1.8.3** for templates, views, and controllers, backed by **Vanilla CSS** (`theme.css`) implementing a premium Government of Telangana design theme. Local state management is handled using `localStorage` to pass estimate metadata between the measurement page and the abstract page.
- **Backend**: **Spring Boot 3.5.14** REST API exposing controller endpoints.
- **Persistence**: **Spring Data JPA** mapping entities to a **PostgreSQL** database.
- **Location Hierarchy**: Cascading geographical dropdown levels (CORP → Zone → Division → Circle → Ward) parsed from spreadsheet data and enforced both on frontend selection menus and backend access checks.

---

## 3. Database Schema Design (Entity-Relationship)

### Estimates Table (`estimates`)
Stores parent estimate documents.
- `id` (INTEGER, PK): Identity auto-increment.
- `name_of_work` (VARCHAR 500): Description of the work.
- `gst_percent` (DOUBLE): GST rate applied.
- `grand_total` (DOUBLE): Total computed cost (Part I + Part II + Part III).
- `created_at` (TIMESTAMP): Date and time of creation.
- `unforeseen_amount` (DOUBLE): Part-III LS provisions/rounding amount.
- `corp` (VARCHAR 100), `zone_name` (VARCHAR 200), `division` (VARCHAR 100), `circle_name` (VARCHAR 200), `ward_name` (VARCHAR 200): Geographical bounds of the estimate.
- `officer_phone` (VARCHAR 20): The creator/handler officer's phone number.
- `status` (VARCHAR 50): Transition states (`DRAFT`, `SUBMITTED_TO_DGM`, `SUBMITTED_TO_GM`, `SUBMITTED_TO_CGM`, `SUBMITTED_TO_DOP`, `APPROVED`).
- `prepared_by_name`/`designation`, `verified_by_name`/`designation`, `recommended_by_name`/`designation`, `forwarded_by_name`/`designation`, `sanctioned_by_name`/`designation`: Signatures updated during workflow transitions.
- `version` (BIGINT): Optimistic locking version number.

### Estimate Items Table (`estimate_items`)
Stores measurement rows belonging to an estimate.
- `id` (INTEGER, PK): Identity auto-increment.
- `estimate_id` (INTEGER, FK): Foreign key mapping back to `estimates` with cascade delete.
- `sno` (INTEGER): Row sequence number.
- `is_material` (VARCHAR 10): Flag (`Yes`/`No`) indicating whether it represents materials (Part-I Cost of Material) or civil items (Part-I Cost of Civil Work).
- `description` (TEXT): Description of the item.
- `num` (DOUBLE): Number of units multiplier.
- `length` (DOUBLE), `breadth` (DOUBLE), `depth` (DOUBLE): Dimension fields.
- `quantity` (DOUBLE): Calculated row quantity (`num * length * breadth * depth`).
- `rate` (DOUBLE): Unit cost rate.
- `unit` (VARCHAR 100): Unit of measurement.
- `amount` (DOUBLE): Row cost amount (`quantity * rate`).

### Item Master Table (`itemlist`)
ReadOnly catalog containing standard schedule of rates (SoR).
- `slno` (INTEGER, PK): Serial code.
- `item_description` (TEXT): Standard description.
- `unit` (VARCHAR 50): Metric unit.
- `rate` (NUMERIC): Rate in Rupees.

### Users Table (`users`)
Stores officer metadata.
- `phone_number` (VARCHAR 20, PK): Unique identification.
- `name` (VARCHAR 100): Officer's name.
- `password` (VARCHAR 100): Encrypted or plain password.
- `designation` (VARCHAR 100): Officer's job title.
- `role` (VARCHAR 50): Default active role (`MANAGER`, `DGM`, `GM`, `CGM`, `DOP`).
- `created_at` (TIMESTAMP): User creation timestamp.

### User Locations Table (`user_locations`)
A one-to-many relationship mapping officers to the specific corp/zones/divisions/circles/wards they have authority over.
- `id` (INTEGER, PK): Auto-increment.
- `phone_number` (VARCHAR 20): Foreign key mapping to user's phone.
- `corp` (VARCHAR 100), `zone_name` (VARCHAR 200), `division` (VARCHAR 100), `circle_name` (VARCHAR 200), `ward_name` (VARCHAR 200): Hierarchy scope.
- `role` (VARCHAR 50): Role scope level for this assignment.

---

## 4. Approval Workflow & Role Rules

The system implements strict hierarchy validation:
1. **MANAGER** (AE - Assistant Engineer):
   - Can create estimates at `DRAFT` status.
   - Access restricted to assigned `Ward`.
   - Forwarding action transitions status to `SUBMITTED_TO_DGM`.
2. **DGM** (Deputy General Manager):
   - Scope: Assigned `Circle` (covers multiple Wards).
   - Action: Can `FORWARD` to `SUBMITTED_TO_GM` (signs `verifiedByName`) or `RETURN` to `DRAFT`.
3. **GM** (General Manager):
   - Scope: Assigned `Division` (covers multiple Circles).
   - Action: Can `FORWARD` to `SUBMITTED_TO_CGM` (signs `recommendedByName`) or `RETURN` to `SUBMITTED_TO_DGM`.
4. **CGM** (Chief General Manager):
   - Scope: Assigned `Zone` (covers multiple Divisions).
   - Action: Can `FORWARD` to `SUBMITTED_TO_DOP` (signs `forwardedByName`) or `RETURN` to `SUBMITTED_TO_GM`.
5. **DOP** (Director of Projects):
   - Scope: Corporate level (entire grid).
   - Action: Can `FORWARD` to `APPROVED` (signs `sanctionedByName`) or `RETURN` to `SUBMITTED_TO_CGM`.

---

## 5. Complete Seed User Directory for Testing

The following logins are preconfigured from the parsed spreadsheet (`users_parsed.json`) with the default password `"1234"`:

| Role | Name | Phone Number | Scope / Assigned Location |
| :--- | :--- | :--- | :--- |
| **DOP** | Vasa SatyaNarayana | `9989999753` | Corporate |
| **CGM** | P.NAGENDRA KUMAR | `9989989507` | Zone: LB Nagar |
| **GM** | M.MAHENDER | `9989994708` | Division: 5 & 6 under LB Nagar |
| **DGM** | T.N SAINATH GOUD | `7331185790` | Circle: 11 (Nagole) under Division 5 |
| **DGM** | D.CHANDRU NAIK | `7331185782` | Circle: 12 (Saroornagar) under Division 5 |
| **DGM** | SRINU RAVULA | `9989996103` | Circle: 13 (LB Nagar) under Division 6 |
| **DGM** | K.NAGAR RAJU | `9989994369` | Circle: 14 (Hayathnagar) under Division 6 |
| **MANAGER** | K.RAMAKRISHNA GOUD | `7995010510` | Wards: 51 & 52 under Circle 11 |
| **MANAGER** | N.PAVITA LAKSHMI | `9989999546` | Wards: 37 & 38 under Circle 13 |
| **MANAGER** | C.BANU PRAKASH REDDY | `9154866585` | Wards: 42 & 43 under Circle 14 |
| **MANAGER** | P.BHAVYA | `9154866671` | Wards: 39 & 41 under Circle 13 |
| **MANAGER** | BANDLA SANDEEP | `9154866620` | Wards: 46 & 47 under Circle 11 |
| **MANAGER** | G.RAJITHA | `9154297397` | Wards: 34 & 35 under Circle 12 |
| **MANAGER** | B.LENIN | `7995010496` | Ward: 45 under Circle 11 |
| **MANAGER** | BOMMIDI PRIYANKA | `9154866643` | Wards: 44 & 48 under Circle 14 |
| **MANAGER** | SANGOJU SIRIVENNELA | `9154866717` | Ward: 49 under Circle 14 |
| **MANAGER** | KATKURI MAMATHA | `9154866634` | Wards: 32 & 33 under Circle 12 |
| **MANAGER** | SURA SRAVANTHI REDDY | `9154866700` | Wards: 30 & 31 under Circle 12 |

---

## 6. Complete Source Code Files

Below are the exact code contents for every file in the codebase.

### 6.1 Backend Configuration & Entrypoint

#### File: `pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.5.14</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.hmwssb</groupId>
	<artifactId>works</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name/>
	<description/>
	<url/>
	<licenses>
		<license/>
	</licenses>
	<developers>
		<developer/>
	</developers>
	<scm>
		<connection/>
		<developerConnection/>
		<tag/>
		<url/>
	</scm>
	<properties>
		<java.version>21</java.version>
	</properties>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>

		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>

</project>
```

#### File: `src/main/resources/application.properties`
```properties
spring.application.name=works
# ── Server ────────────────────────────────────────────────────────────────────
server.port=8080

# ── PostgreSQL DataSource ──────────────────────────────────────────────────────
# Update host/port/dbname/credentials to match your pgAdmin setup.
# From your pgAdmin screenshot the database is named "Works" under PostgreSQL 16.
spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=hmwssb
spring.datasource.driver-class-name=org.postgresql.Driver

# ── JPA / Hibernate ────────────────────────────────────────────────────────────
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# "validate"  → checks schema matches entities on startup (safe for production)
# "update"    → auto-alters schema (use only during development)
# "none"      → do nothing (use when schema is managed externally)
spring.jpa.hibernate.ddl-auto=update

spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Default schema so you don't need schema= on every @Table
spring.jpa.properties.hibernate.default_schema=public

# ── Jackson (JSON) ────────────────────────────────────────────────────────────
# Convert Java camelCase field names to JSON camelCase (itemDescription, not item_description)
spring.jackson.property-naming-strategy=LOWER_CAMEL_CASE
```

#### File: `src/main/java/com/hmwssb/works/WorksApplication.java`
```java
package com.hmwssb.works;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WorksApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorksApplication.class, args);
	}

}
```

---

### 6.2 Data Models (JPA Entities)

#### File: `src/main/java/com/hmwssb/works/model/User.java`
```java
package com.hmwssb.works.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users", schema = "public")
public class User {

    @Id
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "role", length = 50)
    private String role = "OFFICER";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "phone_number")
    private List<UserLocation> locations = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<UserLocation> getLocations() { return locations; }
    public void setLocations(List<UserLocation> locations) { this.locations = locations; }
}
```

#### File: `src/main/java/com/hmwssb/works/model/UserLocation.java`
```java
package com.hmwssb.works.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_locations", schema = "public")
public class UserLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "phone_number", length = 20, insertable = false, updatable = false)
    private String phoneNumber;

    @Column(name = "corp", length = 100)
    private String corp;

    @Column(name = "zone_name", length = 200)
    private String zoneName;

    @Column(name = "division", length = 100)
    private String division;

    @Column(name = "circle_name", length = 200)
    private String circleName;

    @Column(name = "ward_name", length = 200)
    private String wardName;

    @Column(name = "role", length = 50)
    private String role;

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getCorp() { return corp; }
    public void setCorp(String corp) { this.corp = corp; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public String getCircleName() { return circleName; }
    public void setCircleName(String circleName) { this.circleName = circleName; }

    public String getWardName() { return wardName; }
    public void setWardName(String wardName) { this.wardName = wardName; }
}
```

#### File: `src/main/java/com/hmwssb/works/model/Item.java`
```java
package com.hmwssb.works.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps to the public.itemlist table in PostgreSQL.
 *
 * Assumed schema:
 *   CREATE TABLE public.itemlist (
 *       slno             INTEGER PRIMARY KEY,
 *       item_description TEXT,
 *       unit             VARCHAR(50),
 *       rate             NUMERIC(12, 100)
 *   );
 */
@Entity
@Table(name = "itemlist", schema = "public")
public class Item {

    @Id
    @Column(name = "slno")
    private Integer slno;

    @Column(name = "item_description", columnDefinition = "TEXT")
    private String itemDescription;

    @Column(name = "unit", length = 100)
    private String unit;

    @Column(name = "rate")
    private Double rate;

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Integer getSlno() { return slno; }
    public void setSlno(Integer slno) { this.slno = slno; }

    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) {
        this.itemDescription = itemDescription;
    }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }
}
```

#### File: `src/main/java/com/hmwssb/works/model/EstimateItem.java`
```java
package com.hmwssb.works.model;

import jakarta.persistence.*;

@Entity
@Table(name = "estimate_items", schema = "public")
public class EstimateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "sno")
    private Integer sno;

    @Column(name = "is_material", length = 10)
    private String isMaterial;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "num")
    private Double num;

    @Column(name = "length")
    private Double length;

    @Column(name = "breadth")
    private Double breadth;

    @Column(name = "depth")
    private Double depth;

    @Column(name = "quantity")
    private Double quantity;

    @Column(name = "rate")
    private Double rate;

    @Column(name = "unit", length = 100)
    private String unit;

    @Column(name = "amount")
    private Double amount;

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getSno() { return sno; }
    public void setSno(Integer sno) { this.sno = sno; }

    public String getIsMaterial() { return isMaterial; }
    public void setIsMaterial(String isMaterial) { this.isMaterial = isMaterial; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getNum() { return num; }
    public void setNum(Double num) { this.num = num; }

    public Double getLength() { return length; }
    public void setLength(Double length) { this.length = length; }

    public Double getBreadth() { return breadth; }
    public void setBreadth(Double breadth) { this.breadth = breadth; }

    public Double getDepth() { return depth; }
    public void setDepth(Double depth) { this.depth = depth; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}
```

#### File: `src/main/java/com/hmwssb/works/model/Estimate.java`
```java
package com.hmwssb.works.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "estimates", schema = "public")
public class Estimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name_of_work", length = 500)
    private String nameOfWork;

    @Column(name = "gst_percent")
    private Double gstPercent;

    @Column(name = "grand_total")
    private Double grandTotal;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "estimate_id")
    @OrderBy("sno ASC")
    private List<EstimateItem> items = new ArrayList<>();

    @Column(name = "unforeseen_amount")
    private Double unforeseenAmount = 0.0;

    @Column(name = "corp", length = 100)
    private String corp;

    @Column(name = "zone_name", length = 200)
    private String zoneName;

    @Column(name = "division", length = 100)
    private String division;

    @Column(name = "circle_name", length = 200)
    private String circleName;

    @Column(name = "ward_name", length = 200)
    private String wardName;

    @Column(name = "officer_phone", length = 20)
    private String officerPhone;

    @Column(name = "status", length = 50)
    private String status = "DRAFT";

    @Column(name = "prepared_by_name", length = 100)
    private String preparedByName;

    @Column(name = "prepared_by_designation", length = 100)
    private String preparedByDesignation;

    @Column(name = "verified_by_name", length = 100)
    private String verifiedByName;

    @Column(name = "verified_by_designation", length = 100)
    private String verifiedByDesignation;

    @Column(name = "recommended_by_name", length = 100)
    private String recommendedByName;

    @Column(name = "recommended_by_designation", length = 100)
    private String recommendedByDesignation;

    @Column(name = "forwarded_by_name", length = 100)
    private String forwardedByName;

    @Column(name = "forwarded_by_designation", length = 100)
    private String forwardedByDesignation;

    @Column(name = "sanctioned_by_name", length = 100)
    private String sanctionedByName;

    @Column(name = "sanctioned_by_designation", length = 100)
    private String sanctionedByDesignation;

    @Version
    @Column(name = "version")
    private Long version;

    // ── Lifecycle Hook ──────────────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "DRAFT";
        }
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNameOfWork() { return nameOfWork; }
    public void setNameOfWork(String nameOfWork) { this.nameOfWork = nameOfWork; }

    public Double getGstPercent() { return gstPercent; }
    public void setGstPercent(Double gstPercent) { this.gstPercent = gstPercent; }

    public Double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(Double grandTotal) { this.grandTotal = grandTotal; }

    public Double getUnforeseenAmount() { return unforeseenAmount; }
    public void setUnforeseenAmount(Double unforeseenAmount) { this.unforeseenAmount = unforeseenAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<EstimateItem> getItems() { return items; }
    public void setItems(List<EstimateItem> items) { this.items = items; }

    public String getCorp() { return corp; }
    public void setCorp(String corp) { this.corp = corp; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public String getCircleName() { return circleName; }
    public void setCircleName(String circleName) { this.circleName = circleName; }

    public String getWardName() { return wardName; }
    public void setWardName(String wardName) { this.wardName = wardName; }

    public String getOfficerPhone() { return officerPhone; }
    public void setOfficerPhone(String officerPhone) { this.officerPhone = officerPhone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPreparedByName() { return preparedByName; }
    public void setPreparedByName(String preparedByName) { this.preparedByName = preparedByName; }

    public String getPreparedByDesignation() { return preparedByDesignation; }
    public void setPreparedByDesignation(String preparedByDesignation) { this.preparedByDesignation = preparedByDesignation; }

    public String getVerifiedByName() { return verifiedByName; }
    public void setVerifiedByName(String verifiedByName) { this.verifiedByName = verifiedByName; }

    public String getVerifiedByDesignation() { return verifiedByDesignation; }
    public void setVerifiedByDesignation(String verifiedByDesignation) { this.verifiedByDesignation = verifiedByDesignation; }

    public String getRecommendedByName() { return recommendedByName; }
    public void setRecommendedByName(String recommendedByName) { this.recommendedByName = recommendedByName; }

    public String getRecommendedByDesignation() { return recommendedByDesignation; }
    public void setRecommendedByDesignation(String recommendedByDesignation) { this.recommendedByDesignation = recommendedByDesignation; }

    public String getForwardedByName() { return forwardedByName; }
    public void setForwardedByName(String forwardedByName) { this.forwardedByName = forwardedByName; }

    public String getForwardedByDesignation() { return forwardedByDesignation; }
    public void setForwardedByDesignation(String forwardedByDesignation) { this.forwardedByDesignation = forwardedByDesignation; }

    public String getSanctionedByName() { return sanctionedByName; }
    public void setSanctionedByName(String sanctionedByName) { this.sanctionedByName = sanctionedByName; }

    public String getSanctionedByDesignation() { return sanctionedByDesignation; }
    public void setSanctionedByDesignation(String sanctionedByDesignation) { this.sanctionedByDesignation = sanctionedByDesignation; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
```

---

### 6.3 Repositories

#### File: `src/main/java/com/hmwssb/works/repository/UserRepository.java`
```java
package com.hmwssb.works.repository;

import com.hmwssb.works.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
}
```

#### File: `src/main/java/com/hmwssb/works/repository/ItemRepository.java`
```java
package com.hmwssb.works.repository;

import com.hmwssb.works.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, Integer>, JpaSpecificationExecutor<Item> {

    /**
     * Case-insensitive full-text search on item_description.
     * Uses PostgreSQL ILIKE for partial matching anywhere in the string.
     */
    @Query(value = """
            SELECT * FROM public.itemlist
            WHERE item_description ILIKE CONCAT('%', :query, '%')
            ORDER BY slno
            """, nativeQuery = true)
    List<Item> searchByDescription(@Param("query") String query);
}
```

#### File: `src/main/java/com/hmwssb/works/repository/EstimateRepository.java`
```java
package com.hmwssb.works.repository;

import com.hmwssb.works.model.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EstimateRepository extends JpaRepository<Estimate, Integer> {
}
```

---

### 6.4 Controllers

#### File: `src/main/java/com/hmwssb/works/controller/HomeController.java`
```java
package com.hmwssb.works.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/login.html";
    }
}
```

#### File: `src/main/java/com/hmwssb/works/controller/UserController.java`
```java
package com.hmwssb.works.controller;

import com.hmwssb.works.model.User;
import com.hmwssb.works.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String phoneNumber = credentials.get("phoneNumber");
        String password = credentials.get("password");

        if (phoneNumber == null || phoneNumber.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Phone number and password are required.");
        }

        return userRepository.findById(phoneNumber.strip())
                .map(user -> {
                    if (user.getPassword().equals(password)) {
                        return ResponseEntity.ok(user);
                    } else {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid phone number or password.");
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User account not found."));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (user.getPhoneNumber() == null || user.getPhoneNumber().strip().isEmpty()) {
            return ResponseEntity.badRequest().body("Phone number is required and acts as the unique ID.");
        }
        if (user.getName() == null || user.getName().strip().isEmpty()) {
            return ResponseEntity.badRequest().body("User name is required.");
        }
        if (user.getPassword() == null || user.getPassword().strip().isEmpty()) {
            user.setPassword("1234"); // Default password
        }

        // Standardize phone number format
        user.setPhoneNumber(user.getPhoneNumber().strip());

        if (userRepository.existsById(user.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("A user with this phone number already exists.");
        }

        // Set phone number relation back in locations if not set
        if (user.getLocations() != null) {
            user.getLocations().forEach(loc -> loc.setPhoneNumber(user.getPhoneNumber()));
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }
}
```

#### File: `src/main/java/com/hmwssb/works/controller/ItemController.java`
```java
package com.hmwssb.works.controller;

import com.hmwssb.works.model.Item;
import com.hmwssb.works.repository.ItemRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for item master data.
 * Endpoint: GET /api/items/search?q={query}
 */
@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "*")
public class ItemController {

    private final ItemRepository itemRepository;

    public ItemController(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Search items by description using substring matching.
     * The query is matched anywhere inside item_description, case-insensitively.
     */
    @GetMapping("/search")
    public ResponseEntity<List<Item>> search(
            @RequestParam(name = "q", defaultValue = "") String q) {

        String query = q.strip();
        if (query.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        String[] words = query.split("\\s+");
        Specification<Item> spec = (root, query1, cb) -> cb.conjunction();
        for (String word : words) {
            if (!word.isBlank()) {
                final String lowerWord = "%" + word.toLowerCase() + "%";
                spec = spec.and((root, query1, criteriaBuilder) -> criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("itemDescription")),
                        lowerWord));
            }
        }

        List<Item> results = itemRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "slno"));
        return ResponseEntity.ok(results);
    }
}
```

#### File: `src/main/java/com/hmwssb/works/controller/EstimateController.java`
```java
package com.hmwssb.works.controller;

import com.hmwssb.works.model.Estimate;
import com.hmwssb.works.model.User;
import com.hmwssb.works.model.UserLocation;
import com.hmwssb.works.repository.EstimateRepository;
import com.hmwssb.works.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/estimates")
@CrossOrigin(origins = "*")
public class EstimateController {

    private final EstimateRepository estimateRepository;
    private final UserRepository userRepository;

    public EstimateController(EstimateRepository estimateRepository, UserRepository userRepository) {
        this.estimateRepository = estimateRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Estimate estimate) {
        String phone = estimate.getOfficerPhone();
        if (phone == null || phone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Officer phone is required.");
        }

        return userRepository.findById(phone.strip())
            .map(user -> {
                if (estimate.getId() == null) {
                    if (!hasRoleAtLocation(user, "MANAGER", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a MANAGER assigned to this ward can create this estimate.");
                    }
                    estimate.setStatus("DRAFT");
                    estimate.setPreparedByName(user.getName());
                    estimate.setPreparedByDesignation(user.getDesignation());
                    Estimate saved = estimateRepository.save(estimate);
                    return ResponseEntity.ok(saved);
                } else {
                    return estimateRepository.findById(estimate.getId())
                        .map(existing -> {
                            String currentStatus = existing.getStatus();
                            if (currentStatus == null) currentStatus = "DRAFT";

                            String requiredRole = null;
                            if ("DRAFT".equalsIgnoreCase(currentStatus)) {
                                requiredRole = "MANAGER";
                            } else if ("SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) {
                                requiredRole = "DGM";
                            } else if ("SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) {
                                requiredRole = "GM";
                            } else if ("SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) {
                                requiredRole = "CGM";
                            }

                            if (requiredRole == null || !hasRoleAtLocation(user, requiredRole, existing.getCorp(), existing.getZoneName(), existing.getDivision(), existing.getCircleName(), existing.getWardName())) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body("You do not have permission to edit this estimate at its current status: " + currentStatus);
                            }

                            existing.setNameOfWork(estimate.getNameOfWork());
                            existing.setGstPercent(estimate.getGstPercent());
                            existing.setUnforeseenAmount(estimate.getUnforeseenAmount());
                            existing.setGrandTotal(estimate.getGrandTotal());
                            existing.setCorp(estimate.getCorp());
                            existing.setZoneName(estimate.getZoneName());
                            existing.setDivision(estimate.getDivision());
                            existing.setCircleName(estimate.getCircleName());
                            existing.setWardName(estimate.getWardName());

                            existing.getItems().clear();
                            if (estimate.getItems() != null) {
                                existing.getItems().addAll(estimate.getItems());
                            }

                            Estimate saved = estimateRepository.save(existing);
                            return ResponseEntity.ok(saved);
                        })
                        .map(res -> (ResponseEntity<?>) res)
                        .orElseGet(() -> ResponseEntity.notFound().build());
                }
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Officer account not found."));
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(name = "officerPhone", required = false) String officerPhone,
            @RequestParam(name = "role", required = false) String activeRole,
            @RequestParam(name = "corp", required = false) String corp,
            @RequestParam(name = "zoneName", required = false) String zoneName,
            @RequestParam(name = "division", required = false) String division,
            @RequestParam(name = "circleName", required = false) String circleName,
            @RequestParam(name = "wardName", required = false) String wardName) {

        if (officerPhone == null || officerPhone.trim().isEmpty()) {
            return ResponseEntity.ok(estimateRepository.findAll());
        }

        java.util.Optional<User> userOpt = userRepository.findById(officerPhone.strip());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Officer account not found.");
        }
        User user = userOpt.get();

        if (activeRole == null || activeRole.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        boolean hasAssignment = hasRoleAtLocation(user, activeRole, corp, zoneName, division, circleName, wardName);

        if (!hasAssignment) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have access to this location/role scope.");
        }

        List<Estimate> all = estimateRepository.findAll();
        List<Estimate> filtered = all.stream().filter(est -> {
            if ("DOP".equalsIgnoreCase(activeRole)) {
                return true;
            }
            if ("CGM".equalsIgnoreCase(activeRole)) {
                return safeEquals(corp, est.getCorp()) && safeEquals(zoneName, est.getZoneName());
            }
            if ("GM".equalsIgnoreCase(activeRole)) {
                return safeEquals(corp, est.getCorp()) && safeEquals(zoneName, est.getZoneName()) && safeEquals(division, est.getDivision());
            }
            if ("DGM".equalsIgnoreCase(activeRole)) {
                return safeEquals(corp, est.getCorp()) && safeEquals(zoneName, est.getZoneName()) && safeEquals(division, est.getDivision()) && safeEquals(circleName, est.getCircleName());
            }
            if ("MANAGER".equalsIgnoreCase(activeRole)) {
                return safeEquals(corp, est.getCorp()) && safeEquals(zoneName, est.getZoneName()) && safeEquals(division, est.getDivision()) && safeEquals(circleName, est.getCircleName()) && safeEquals(wardName, est.getWardName());
            }
            return false;
        }).toList();

        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Estimate> get(@PathVariable(name = "id") Integer id) {
        return estimateRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable(name = "id") Integer id) {
        if (estimateRepository.existsById(id)) {
            estimateRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<?> performAction(
            @PathVariable(name = "id") Integer id,
            @RequestBody Map<String, String> payload) {
        String action = payload.get("action"); // "FORWARD" or "RETURN"
        String officerPhone = payload.get("officerPhone");

        if (action == null || officerPhone == null) {
            return ResponseEntity.badRequest().body("Action and officerPhone are required.");
        }

        java.util.Optional<User> userOpt = userRepository.findById(officerPhone.strip());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Officer account not found.");
        }
        User user = userOpt.get();

        java.util.Optional<Estimate> estimateOpt = estimateRepository.findById(id);
        if (estimateOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Estimate estimate = estimateOpt.get();

        String currentStatus = estimate.getStatus();
        if (currentStatus == null) currentStatus = "DRAFT";

        if ("FORWARD".equalsIgnoreCase(action)) {
            if ("DRAFT".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "MANAGER", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a MANAGER for this ward can forward this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_DGM");
                estimate.setPreparedByName(user.getName());
                estimate.setPreparedByDesignation(user.getDesignation());
            } else if ("SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "DGM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a DGM for this circle can verify this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_GM");
                estimate.setVerifiedByName(user.getName());
                estimate.setVerifiedByDesignation(user.getDesignation());
            } else if ("SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "GM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a GM for this division can recommend this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_CGM");
                estimate.setRecommendedByName(user.getName());
                estimate.setRecommendedByDesignation(user.getDesignation());
            } else if ("SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "CGM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a CGM for this zone can forward this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_DOP");
                estimate.setForwardedByName(user.getName());
                estimate.setForwardedByDesignation(user.getDesignation());
            } else if ("SUBMITTED_TO_DOP".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "DOP", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a DOP can approve/sanction this estimate.");
                }
                estimate.setStatus("APPROVED");
                estimate.setSanctionedByName(user.getName());
                estimate.setSanctionedByDesignation(user.getDesignation());
            } else {
                return ResponseEntity.badRequest().body("Invalid forward transition for status " + currentStatus);
            }
        } else if ("RETURN".equalsIgnoreCase(action)) {
            if ("SUBMITTED_TO_DGM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "DGM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a DGM for this circle can return this estimate.");
                }
                estimate.setStatus("DRAFT");
                estimate.setVerifiedByName(null);
                estimate.setVerifiedByDesignation(null);
            } else if ("SUBMITTED_TO_GM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "GM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a GM for this division can return this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_DGM");
                estimate.setRecommendedByName(null);
                estimate.setRecommendedByDesignation(null);
            } else if ("SUBMITTED_TO_CGM".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "CGM", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a CGM for this zone can return this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_GM");
                estimate.setForwardedByName(null);
                estimate.setForwardedByDesignation(null);
            } else if ("SUBMITTED_TO_DOP".equalsIgnoreCase(currentStatus)) {
                if (!hasRoleAtLocation(user, "DOP", estimate.getCorp(), estimate.getZoneName(), estimate.getDivision(), estimate.getCircleName(), estimate.getWardName())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only a DOP can return this estimate.");
                }
                estimate.setStatus("SUBMITTED_TO_CGM");
                estimate.setSanctionedByName(null);
                estimate.setSanctionedByDesignation(null);
            } else {
                return ResponseEntity.badRequest().body("Invalid return transition for status " + currentStatus);
            }
        } else {
            return ResponseEntity.badRequest().body("Invalid action: " + action);
        }

        Estimate saved = estimateRepository.save(estimate);
        return ResponseEntity.ok(saved);
    }

    private boolean hasRoleAtLocation(User user, String requiredRole, String corp, String zoneName, String division, String circleName, String wardName) {
        return user.getLocations().stream().anyMatch(loc -> {
            if (!safeEquals(loc.getRole(), requiredRole)) return false;
            if ("DOP".equalsIgnoreCase(requiredRole)) {
                return safeEquals(loc.getCorp(), "Corporate");
            }
            if ("CGM".equalsIgnoreCase(requiredRole)) {
                return safeEquals(loc.getCorp(), corp) && safeEquals(loc.getZoneName(), zoneName);
            }
            if ("GM".equalsIgnoreCase(requiredRole)) {
                return safeEquals(loc.getCorp(), corp) && safeEquals(loc.getZoneName(), zoneName) && safeEquals(loc.getDivision(), division);
            }
            if ("DGM".equalsIgnoreCase(requiredRole)) {
                return safeEquals(loc.getCorp(), corp) && safeEquals(loc.getZoneName(), zoneName) && safeEquals(loc.getDivision(), division) && safeEquals(loc.getCircleName(), circleName);
            }
            if ("MANAGER".equalsIgnoreCase(requiredRole)) {
                return safeEquals(loc.getCorp(), corp) && safeEquals(loc.getZoneName(), zoneName) && safeEquals(loc.getDivision(), division) && safeEquals(loc.getCircleName(), circleName) && safeEquals(loc.getWardName(), wardName);
            }
            return false;
        });
    }

    private boolean safeEquals(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.trim().equalsIgnoreCase(s2.trim());
    }
}
```

---

### 6.5 Database Seeding

#### File: `src/main/java/com/hmwssb/works/seeder/DatabaseSeeder.java`
```java
package com.hmwssb.works.seeder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmwssb.works.model.User;
import com.hmwssb.works.model.UserLocation;
import com.hmwssb.works.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    public DatabaseSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean needsReseed = false;
        if (userRepository.count() > 0) {
            long locationsWithNullRole = userRepository.findAll().stream()
                .flatMap(u -> u.getLocations().stream())
                .filter(loc -> loc.getRole() == null)
                .count();
            if (locationsWithNullRole > 0) {
                needsReseed = true;
            }
        } else {
            needsReseed = true;
        }

        if (!needsReseed) {
            System.out.println("Users table already has " + userRepository.count() + " records with roles populated. Skipping seed.");
            return;
        }

        System.out.println("Clearing old users and seeding HMWSSB officer accounts from sample users.xlsx data...");
        userRepository.deleteAll();

        ObjectMapper mapper = new ObjectMapper();
        ClassPathResource resource = new ClassPathResource("users_parsed.json");
        if (!resource.exists()) {
            System.err.println("Could not find users_parsed.json in classpath resources!");
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            List<Map<String, Object>> rawUsers = mapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
            List<User> usersToSave = new ArrayList<>();

            for (Map<String, Object> rawUser : rawUsers) {
                User user = new User();
                user.setPhoneNumber(cleanString((String) rawUser.get("phoneNumber")));
                user.setName(cleanString((String) rawUser.get("name")));
                user.setPassword("1234"); // Default password for all officers
                user.setDesignation(cleanString((String) rawUser.get("designation")));
                user.setRole(cleanString((String) rawUser.get("role")));

                List<Map<String, Object>> rawLocs = (List<Map<String, Object>>) rawUser.get("locations");
                List<UserLocation> locations = new ArrayList<>();
                if (rawLocs != null) {
                    for (Map<String, Object> rawLoc : rawLocs) {
                        UserLocation loc = new UserLocation();
                        loc.setCorp(cleanString((String) rawLoc.get("corp")));
                        loc.setZoneName(cleanString((String) rawLoc.get("zoneName")));
                        loc.setDivision(cleanString((String) rawLoc.get("division")));
                        loc.setCircleName(cleanString((String) rawLoc.get("circleName")));
                        loc.setWardName(cleanString((String) rawLoc.get("wardName")));
                        loc.setRole(cleanString((String) rawLoc.get("role")));
                        loc.setPhoneNumber(user.getPhoneNumber());
                        locations.add(loc);
                    }
                }
                user.setLocations(locations);
                usersToSave.add(user);
            }

            userRepository.saveAll(usersToSave);
            System.out.println("Successfully seeded " + usersToSave.size() + " officer accounts from sample users.xlsx!");
        } catch (Exception e) {
            System.err.println("Error seeding users from json file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String cleanString(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.replace("â€“", "-")
                              .replace("â\u0080\u0093", "-")
                              .replace("?", "-")
                              .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
```

---

### 6.6 Frontend Scripts & Configurations

#### File: `src/main/resources/static/config.js`
```javascript
/* ═══════════════════════════════════════════════════════════════════════════
   HMWSSB Works System - Application Configuration
   ═══════════════════════════════════════════════════════════════════════════ */

var APP_CONFIG = {
  API_BASE: '/api',
  SEARCH_DEBOUNCE_MS: 300,
  MIN_SEARCH_LENGTH: 2,
  GST_OPTIONS: [0, 5, 12, 18],
  SESSION_KEY: 'hmwssb_logged_in_user',
  ESTIMATE_KEY: 'current_estimate_data',
  SESSION_TIMEOUT_HOURS: 8
};
```

#### File: `src/main/resources/static/app.js`
```javascript
/* ═══════════════════════════════════════════════════════════════════════════
   HMWSSB Works System - Shared AngularJS Services
   ═══════════════════════════════════════════════════════════════════════════ */

var hmwssbShared = angular.module('hmwssbShared', []);

/* ── AuthService ── */
hmwssbShared.factory('AuthService', ['$window', function ($window) {
  return {
    getUser: function () {
      var raw = $window.localStorage.getItem(APP_CONFIG.SESSION_KEY);
      if (!raw) return null;
      try {
        var user = JSON.parse(raw);
        if (user._loginTimestamp) {
          var hours = (Date.now() - user._loginTimestamp) / (1000 * 60 * 60);
          if (hours > APP_CONFIG.SESSION_TIMEOUT_HOURS) {
            this.logout();
            return null;
          }
        }
        return user;
      } catch (e) {
        return null;
      }
    },

    setUser: function (user) {
      user._loginTimestamp = Date.now();
      $window.localStorage.setItem(APP_CONFIG.SESSION_KEY, JSON.stringify(user));
    },

    isLoggedIn: function () {
      return this.getUser() !== null;
    },

    logout: function () {
      $window.localStorage.removeItem(APP_CONFIG.SESSION_KEY);
      $window.localStorage.removeItem(APP_CONFIG.ESTIMATE_KEY);
      $window.location.href = 'login.html';
    },

    requireLogin: function () {
      if (!this.isLoggedIn()) {
        $window.location.href = 'login.html';
        return false;
      }
      return true;
    }
  };
}]);

/* ── StatusService ── */
hmwssbShared.factory('StatusService', [function () {
  var STATUS_ORDER = ['DRAFT', 'SUBMITTED_TO_DGM', 'SUBMITTED_TO_GM', 'SUBMITTED_TO_CGM', 'SUBMITTED_TO_DOP', 'APPROVED'];

  return {
    getStatusOrder: function () { return STATUS_ORDER; },

    getLabel: function (status) {
      if (!status) return 'Draft';
      switch (status) {
        case 'DRAFT': return 'Draft';
        case 'SUBMITTED_TO_DGM': return 'Pending DGM';
        case 'SUBMITTED_TO_GM': return 'Pending GM';
        case 'SUBMITTED_TO_CGM': return 'Pending CGM';
        case 'SUBMITTED_TO_DOP': return 'Pending DOP';
        case 'APPROVED': return 'Approved';
        default: return status;
      }
    },

    getBadgeClass: function (status) {
      if (!status || status === 'DRAFT') return 'badge-draft';
      if (status === 'APPROVED') return 'badge-approved';
      return 'badge-pending';
    },

    isEditable: function (status, role) {
      status = status || 'DRAFT';
      role = (role || '').toUpperCase();
      if (status === 'DRAFT' && role === 'MANAGER') return true;
      if (status === 'SUBMITTED_TO_DGM' && role === 'DGM') return true;
      if (status === 'SUBMITTED_TO_GM' && role === 'GM') return true;
      if (status === 'SUBMITTED_TO_CGM' && role === 'CGM') return true;
      return false;
    },

    canForward: function (status, role) {
      status = status || 'DRAFT';
      role = (role || '').toUpperCase();
      if (status === 'DRAFT' && role === 'MANAGER') return true;
      if (status === 'SUBMITTED_TO_DGM' && role === 'DGM') return true;
      if (status === 'SUBMITTED_TO_GM' && role === 'GM') return true;
      if (status === 'SUBMITTED_TO_CGM' && role === 'CGM') return true;
      if (status === 'SUBMITTED_TO_DOP' && role === 'DOP') return true;
      return false;
    },

    canReturn: function (status, role) {
      status = status || 'DRAFT';
      role = (role || '').toUpperCase();
      if (status === 'SUBMITTED_TO_DGM' && role === 'DGM') return true;
      if (status === 'SUBMITTED_TO_GM' && role === 'GM') return true;
      if (status === 'SUBMITTED_TO_CGM' && role === 'CGM') return true;
      if (status === 'SUBMITTED_TO_DOP' && role === 'DOP') return true;
      return false;
    },

    getForwardLabel: function (status) {
      switch (status) {
        case 'DRAFT': return 'Forward to DGM';
        case 'SUBMITTED_TO_DGM': return 'Forward to GM';
        case 'SUBMITTED_TO_GM': return 'Forward to CGM';
        case 'SUBMITTED_TO_CGM': return 'Forward to DOP';
        case 'SUBMITTED_TO_DOP': return 'Approve & Sanction';
        default: return 'Forward';
      }
    },

    getReturnLabel: function (status) {
      switch (status) {
        case 'SUBMITTED_TO_DGM': return 'Manager (AE)';
        case 'SUBMITTED_TO_GM': return 'DGM';
        case 'SUBMITTED_TO_CGM': return 'GM';
        case 'SUBMITTED_TO_DOP': return 'CGM';
        default: return 'Previous Officer';
      }
    },

    getStepIndex: function (status) {
      return STATUS_ORDER.indexOf(status || 'DRAFT');
    }
  };
}]);

/* ── ModalService ── */
hmwssbShared.factory('ModalService', ['$rootScope', '$compile', '$timeout', function ($rootScope, $compile, $timeout) {
  var modalScope = null;

  function close() {
    if (modalScope) {
      modalScope.$destroy();
      modalScope = null;
    }
    var el = document.getElementById('hmwssb-modal');
    if (el) el.remove();
  }

  return {
    alert: function (title, message, type) {
      type = type || 'info';
      close();
      modalScope = $rootScope.$new();
      modalScope._title = title;
      modalScope._message = message;
      modalScope._type = type;
      modalScope._close = function () { close(); };

      var html = '<div class="modal-overlay" ng-click="_close()">' +
        '<div class="modal-content" ng-click="$event.stopPropagation()">' +
        '<div class="modal-header"><span>{{_title}}</span></div>' +
        '<div class="modal-body"><p>{{_message}}</p></div>' +
        '<div class="modal-footer"><button class="btn btn-primary" ng-click="_close()">OK</button></div>' +
        '</div></div>';

      var el = document.createElement('div');
      el.id = 'hmwssb-modal';
      el.innerHTML = html;
      document.body.appendChild(el);
      $compile(el)(modalScope);
    },

    confirm: function (title, message, onConfirm, type) {
      type = type || 'warning';
      close();
      modalScope = $rootScope.$new();
      modalScope._title = title;
      modalScope._message = message;
      modalScope._type = type;
      modalScope._confirm = function () { close(); if (onConfirm) onConfirm(); };
      modalScope._cancel = function () { close(); };

      var html = '<div class="modal-overlay" ng-click="_cancel()">' +
        '<div class="modal-content" ng-click="$event.stopPropagation()">' +
        '<div class="modal-header" style="background:#e67e22;"><span>{{_title}}</span></div>' +
        '<div class="modal-body"><p>{{_message}}</p></div>' +
        '<div class="modal-footer">' +
        '<button class="btn btn-secondary" ng-click="_cancel()">Cancel</button>' +
        '<button class="btn btn-primary" ng-click="_confirm()">Confirm</button>' +
        '</div></div></div>';

      var el = document.createElement('div');
      el.id = 'hmwssb-modal';
      el.innerHTML = html;
      document.body.appendChild(el);
      $compile(el)(modalScope);
    }
  };
}]);

/* ── Utility Functions ── */
hmwssbShared.factory('Utils', [function () {
  return {
    isMaterialYes: function (val) {
      if (val === undefined || val === null) return false;
      var s = String(val).trim().toLowerCase();
      return s === 'yes' || s === 'true' || s === '1' || s === 'y';
    },

    formatDate: function (dateStr) {
      if (!dateStr) return '';
      var d = new Date(dateStr);
      return d.toLocaleDateString('en-IN') + ' ' + d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
    },

    numberToWords: function (num) {
      if (num === 0) return 'Rupees Zero Only';

      var ones = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
        'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
      var tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

      function helper(n) {
        if (n < 20) return ones[n];
        if (n < 100) return tens[Math.floor(n / 10)] + (n % 10 !== 0 ? ' ' + ones[n % 10] : '');
        if (n < 1000) return ones[Math.floor(n / 100)] + ' Hundred' + (n % 100 !== 0 ? ' ' + helper(n % 100) : '');
        if (n < 100000) return helper(Math.floor(n / 1000)) + ' Thousand' + (n % 1000 !== 0 ? ' ' + helper(n % 1000) : '');
        if (n < 10000000) return helper(Math.floor(n / 100000)) + ' Lakh' + (n % 100000 !== 0 ? ' ' + helper(n % 100000) : '');
        return helper(Math.floor(n / 10000000)) + ' Crore' + (n % 10000000 !== 0 ? ' ' + helper(n % 10000000) : '');
      }

      var roundedNum = Math.round(num * 100) / 100;
      var parts = String(roundedNum).split('.');
      var rupees = parseInt(parts[0], 10);
      var paise = parts[1] ? parseInt(parts[1], 10) : 0;

      var result = 'Rupees ' + helper(rupees);
      if (paise > 0) {
        result += ' and ' + helper(paise) + ' Paise';
      }
      result += ' Only';
      return result;
    }
  };
}]);
```

#### File: `src/main/resources/static/hierarchy.js`
```javascript
const WARD_HIERARCHY = {
  "MMC": {
    "Malkajgiri": {
      "1": {
        "1 - Keesara": [
          "1 - Keesara",
          "2 - Chandrapuri Colony",
          "3 - Jawahar Nagar",
          "4 - Dammaiguda",
          "189 - Yapral",
          "300 - Shamirpet"
        ],
        "2 - Alwal": [
          "190 - Turkapally",
          "191 - Macha Bollaram",
          "192 - Temple Alwal",
          "193 - Venkatapuram",
          "194 - Bhudevi Nagar",
          "195 - Kanajiguda"
        ]
      },
      "2": {
        "3 - Bowenpally": [
          "196 - Monda Market",
          "260 - Fateh Nagar",
          "261 - Prakash Nagar",
          "262 - Old Bowenpally",
          "264 - Hasmathpet"
        ],
        "4 - Moula Ali": [
          "184 - Balram Nagar",
          "185 - Vinayak Nagar",
          "186 - Moula Ali",
          "187 - Kakatiya Nagar",
          "188 - Neredmet"
        ],
        "5 - Malkajgiri": [
          "180 - East Anandbagh",
          "181 - Mirjalguda",
          "182 - Goutham Nagar",
          "183 - Malkajgiri"
        ]
      }
    },
    "Uppal": {
      "3": {
        "6 - Ghatkesar": [
          "5 - Nagaram",
          "6 - Ghatkesar",
          "7 - Edulabad",
          "8 - Pocharam"
        ],
        "7 - Kapra": [
          "13 - Vampuguda",
          "14 - Kapra",
          "15 - Dr AS Rao Nagar",
          "16 - Kushaiguda",
          "17 - Cherlapally"
        ]
      },
      "4": {
        "8 - Nacharam": [
          "18 - Shakthi Sai Nagar",
          "19 - H.B. Colony",
          "20 - Mallapur",
          "21 - Nacharam",
          "22 - HMT Nagar"
        ],
        "9 - Uppal": [
          "23 - Chilkanagar",
          "24 - Beerappagadda",
          "25 - Habsiguda",
          "26 - Ramanthapur",
          "27 - Venkat Reddy Nagar",
          "28 - Uppal"
        ],
        "10 - Boduppal": [
          "9 - Medipally",
          "10 - Peerzadiguda",
          "11 - Boduppal",
          "12 - Chengicherla"
        ]
      }
    },
    "LB Nagar": {
      "5": {
        "11 - Nagole": [
          "29 - Nagole",
          "45 - Mansoorabad",
          "46 - GSI",
          "47 - Lecturers Colony",
          "51 - Kuntloor",
          "52 - Pedda Amberpet"
        ],
        "12 - Saroornagar": [
          "30 - Kothapet",
          "31 - Chaitanyapuri",
          "32 - Gaddiannaram",
          "33 - Saroornagar",
          "34 - Doctors Colony",
          "35 - RK Puram",
          "36 - NTR Nagar"
        ]
      },
      "6": {
        "13 - LB Nagar": [
          "37 - Lingojiguda",
          "38 - Champapet",
          "39 - Kharmanghat",
          "40 - Bairamalguda",
          "41 - Hastinapuram"
        ],
        "14 - Hayathnagar": [
          "42 - BN Reddy Nagar",
          "43 - Vanasthalipuram",
          "44 - Chintalkunta",
          "48 - High Court Colony",
          "49 - Sahebnagar",
          "50 - Hayathnagar"
        ]
      }
    }
  },
  "HMC": {
    "Shamshabad": {
      "7": {
        "15 - Adibatla": [
          "53 - Thorrur",
          "54 - Kongara Kalan",
          "55 - Adibatla",
          "56 - Turkayamjal"
        ],
        "16 - Badangpet": [
          "57 - Nadargul",
          "58 - Prashanthi Hills",
          "59 - Jillelaguda",
          "60 - Meerpet",
          "61 - Badangpet",
          "62 - Balapur"
        ]
      },
      "8": {
        "17 - Jalpally": [
          "63 - Shaheen Nagar",
          "64 - Pahadi Shareef",
          "65 - Jalpally"
        ],
        "18 - Shamshabad": [
          "66 - Thukkuguda",
          "67 - Mankhal",
          "118 - Shamshabad",
          "119 - Kothwalguda"
        ]
      }
    },
    "Rajendranagar": {
      "9": {
        "19 - Rajendra Nagar": [
          "120 - Rajendra Nagar",
          "121 - Bandlaguda Jagir",
          "122 - Kismatpur",
          "123 - Hydershahkote"
        ],
        "20 - Attapur": [
          "112 - Attapur",
          "113 - Hyderguda",
          "114 - Suleman Nagar",
          "115 - Shastripuram",
          "116 - Katedan",
          "117 - Mailardevpally"
        ]
      },
      "10": {
        "21 - Bahadurpura": [
          "103 - Doodh Bowli",
          "108 - Teegal Kunta",
          "109 - Chandu Lal Baradari",
          "110 - Ramnasthpura",
          "111 - Kishanbagh"
        ],
        "22 - Falaknuma": [
          "104 - Shah Ali Banda",
          "105 - Falaknuma",
          "106 - Jahanuma",
          "107 - Nawab Saheb Kunta"
        ],
        "23 - Chandrayangutta": [
          "68 - Bandlaguda",
          "69 - Noori Nagar",
          "70 - Barkas",
          "71 - Kanchanbagh",
          "72 - Chandrayangutta"
        ],
        "24 - Jangammet": [
          "73 - Riyasat Nagar",
          "74 - Lalitha Bagh",
          "75 - Jangammet",
          "76 - Phool Bagh",
          "77 - Quadri Chaman"
        ]
      }
    },
    "Charminar": {
      "11": {
        "26 - Yakutpura": [
          "78 - Gowlipura",
          "79 - Talab Chanchalam",
          "80 - Yakutpura",
          "81 - Dabeerpura",
          "82 - Rein Bazar",
          "83 - Madannapet"
        ],
        "28 - Charminar": [
          "97 - Purani Haveli",
          "98 - Pathergatti",
          "99 - Hari Bowli",
          "100 - Qazipura",
          "101 - Ghansi Bazar",
          "102 - Purana Pul"
        ]
      },
      "12": {
        "25 - Santosh Nagar": [
          "84 - Bhanu Nagar",
          "85 - Santosh Nagar",
          "86 - IS SADAN",
          "87 - Saraswati Nagar"
        ],
        "27 - Malakpet": [
          "88 - Saidabad",
          "89 - Asmangadh",
          "93 - Akberbagh",
          "94 - Chawani"
        ],
        "29 - Moosarambagh": [
          "90 - Moosarambagh",
          "91 - Old Malakpet",
          "92 - MCH Colony",
          "95 - Kala Dera",
          "96 - Azampura"
        ]
      }
    },
    "Golconda": {
      "13": {
        "30 - Goshamahal": [
          "148 - Dattatreya Nagar",
          "149 - Manghalhat",
          "150 - Goshamahal",
          "151 - Begum Bazar",
          "152 - Jambagh",
          "153 - Exhibition Grounds"
        ],
        "31 - Karwan": [
          "134 - Langar Houz",
          "135 - Gudimalkapur",
          "136 - Karwan",
          "137 - Tappachabutra",
          "138 - Ziaguda"
        ]
      },
      "14": {
        "32 - Golconda": [
          "129 - Nizam Colony",
          "130 - Nanalnagar",
          "131 - Tolichowki",
          "132 - Golconda",
          "133 - Ibrahimbagh",
          "223 - Shaikpet",
          "224 - OU Colony"
        ],
        "33 - Mehdipatnam": [
          "139 - Asif Nagar",
          "140 - Padmanabha Nagar",
          "141 - Mehdipatnam",
          "142 - Syed Nagar"
        ],
        "34 - Masab Tank": [
          "143 - Vijayanagar Colony",
          "144 - Ahmed Nagar",
          "145 - Shanti Nagar",
          "147 - Mallepally"
        ]
      }
    },
    "Khairatabad": {
      "15": {
        "35 - Khairatabad": [
          "146 - Red Hills",
          "154 - Gunfoundry",
          "217 - Irrum Manzil",
          "218 - Somajiguda",
          "219 - Khairatabad",
          "220 - Himayathnagar"
        ],
        "36 - Jubilee Hills": [
          "215 - Jubilee Hills",
          "216 - Venkateshwara Colony",
          "221 - Banjara Hills",
          "222 - Film Nagar"
        ]
      },
      "16": {
        "37 - Borabanda": [
          "210 - Krishna Nagar",
          "211 - Rahamath Nagar",
          "212 - Karmika Nagar",
          "213 - Rajeev Nagar",
          "214 - Borabanda"
        ],
        "38 - Yousufguda": [
          "205 - Erragadda",
          "206 - Vengal Rao Nagar",
          "207 - Srinagar Colony",
          "208 - Yousufguda",
          "209 - AG Colony"
        ],
        "39 - Ameerpet": [
          "200 - Begumpet",
          "201 - Ameerpet",
          "202 - SR Nagar",
          "203 - BK Guda",
          "204 - Sanathnagar"
        ]
      }
    },
    "Secunderabad": {
      "17": {
        "40 - Kavadiguda": [
          "165 - Gandhi Nagar",
          "166 - Kavadiguda",
          "167 - Bakaram",
          "168 - Bholakpur",
          "197 - Padmarao Nagar",
          "198 - Bansilalpet",
          "199 - Ramgopalpet"
        ],
        "41 - Musheerabad": [
          "163 - Adikmet",
          "164 - Bagh Lingampally",
          "169 - Musheerabad",
          "170 - Ramnagar",
          "171 - Bapuji Nagar"
        ],
        "42 - Amberpet": [
          "155 - BARKATPURA",
          "156 - Kachiguda",
          "157 - Golnaka",
          "158 - Patel Nagar",
          "159 - Amberpet",
          "160 - Bagh Amberpet",
          "161 - Tilak Nagar",
          "162 - Nallakunta"
        ]
      },
      "18": {
        "43 - Tarnaka": [
          "172 - Boudha Nagar",
          "173 - Tarnaka",
          "174 - Seethaphalmandi",
          "175 - Chilkalguda"
        ],
        "44 - Mettuguda": [
          "176 - Mettuguda",
          "177 - Lalapet",
          "178 - North Lalaguda",
          "179 - Addagutta"
        ]
      }
    }
  },
  "CMC": {
    "Serilingampally": {
      "19": {
        "46 - Patancheruvu": [
          "263 - Tellapur",
          "265 - Muthangi",
          "266 - Patancheruvu",
          "267 - JP Colony"
        ],
        "47 - Ameenpur": [
          "268 - Ramachandrapuram (RC Puram)",
          "269 - Bharathi Nagar",
          "270 - Beeramguda",
          "271 - Ameenpur",
          "272 - Bollaram"
        ]
      },
      "20": {
        "45 - Narsingi": [
          "124 - Narsingi",
          "125 - Kokapet",
          "126 - Gandipet",
          "127 - Manikonda",
          "128 - Neknampur"
        ],
        "48 - Miyapur": [
          "236 - Hafeezpet",
          "237 - Madeenaguda",
          "238 - Chanda Nagar",
          "239 - Deepthisri Nagar",
          "240 - Miyapur",
          "241 - Maktha Mahabubpet"
        ],
        "49 - Serilingampally": [
          "225 - Gachibowli",
          "226 - Nallagandla",
          "227 - Serilingampally",
          "228 - Masjid Banda",
          "229 - Sri Ram Nagar",
          "234 - Kondapur"
        ]
      }
    },
    "Kukatpally": {
      "21": {
        "50 - Madhapur": [
          "230 - Anjaiah Nagar",
          "231 - HITEC City",
          "232 - Madhapur",
          "233 - Izzath Nagar",
          "235 - Matrusri Nagar",
          "242 - Mayuri Nagar"
        ],
        "51 - Allwyn Colony": [
          "243 - Hyder Nagar",
          "244 - Bhagya Nagar Colony",
          "245 - Shamshiguda",
          "246 - Allwyn Colony",
          "247 - Vivekananda Nagar Colony",
          "248 - Venkateshwara Nagar"
        ]
      },
      "22": {
        "52 - Kukatpally": [
          "249 - Kukatpally",
          "250 - Balaji Nagar",
          "251 - Vasanth Nagar",
          "252 - KPHB Colony",
          "253 - Kaithalapur",
          "254 - Gayatri Nagar"
        ],
        "53 - Moosapet": [
          "255 - Allapur",
          "256 - Moti Nagar",
          "257 - Moosapet",
          "258 - Prashanth Nagar",
          "259 - Balanagar"
        ]
      }
    },
    "Quthbullapur": {
      "23": {
        "54 - Chintal": [
          "279 - Rodamestri Nagar",
          "280 - Jagathgiri Gutta",
          "281 - Ranga Reddy Nagar",
          "282 - Chintal",
          "283 - Giri Nagar"
        ],
        "55 - Jeedimetla": [
          "284 - Ganesh Nagar",
          "285 - Padma Nagar",
          "286 - Quthbullapur",
          "287 - Pet Basheerabad"
        ],
        "56 - Kompally": [
          "288 - Kompally",
          "289 - Doolapally",
          "290 - Subhash Nagar",
          "292 - Saibaba Nagar"
        ],
        "57 - Gajularamaram": [
          "277 - Mahadevpuram",
          "278 - Gajularamaram",
          "291 - Shapur Nagar",
          "293 - Suraram"
        ]
      },
      "24": {
        "58 - Nizampet": [
          "273 - Nizampet",
          "274 - Bachupally",
          "275 - Bhandari Layout",
          "276 - Pragathi Nagar"
        ],
        "59 - Dundigal": [
          "294 - Bahadurpally",
          "295 - Bowrampet",
          "296 - Dundigal"
        ],
        "60 - Medchal": [
          "297 - Medchal",
          "298 - Pudur - Kistapur",
          "299 - Gundlapochampally"
        ]
      }
    }
  }
};
```

#### File: `src/main/resources/static/styles/theme.css`
```css
/* ═══════════════════════════════════════════════════════════════════════════
   HMWSSB Works System - Shared Theme
   Government of Telangana - Hyderabad Metropolitan Water Supply & Sewerage Board
   ═══════════════════════════════════════════════════════════════════════════ */

:root {
  /* ── Primary Palette ── */
  --primary: #7a7a38;
  --primary-light: #9a9a58;
  --primary-dark: #5a5a22;
  --accent: #d4c97a;
  --accent-light: #e8e0a8;

  /* ── Background ── */
  --bg-gradient: linear-gradient(135deg, #f0f0e8 0%, #e8e8d8 50%, #d8d8c8 100%);
  --bg-card: #f5f5e8;
  --bg-card-alt: rgba(245, 245, 232, 0.95);
  --bg-highlight: #fffde7;
  --bg-white: #fff;

  /* ── Borders ── */
  --border-card: #c8c87a;
  --border-light: #d4c97a;
  --border-input: #ccc;
  --border-input-focus: #7fa8d4;

  /* ── Text ── */
  --text-main: #222;
  --text-muted: #555;
  --text-light: #7f8c8d;
  --text-white: #fff;

  /* ── Table ── */
  --table-header-bg: #a0c4de;
  --table-header-text: #1a3550;
  --table-row-odd: #e8f4fb;
  --table-row-even: #daeef8;
  --table-border: #c8daea;
  --table-header-border: #7fa8c0;

  /* ── Status Colors ── */
  --status-draft-bg: #dbeafe;
  --status-draft-text: #1e40af;
  --status-pending-bg: #fef3c7;
  --status-pending-text: #92400e;
  --status-approved-bg: #dcfce7;
  --status-approved-text: #166534;

  /* ── Actions ── */
  --btn-primary: #2980b9;
  --btn-primary-hover: #1f618d;
  --btn-success: #27ae60;
  --btn-success-hover: #219653;
  --btn-danger: #c0392b;
  --btn-danger-hover: #a93226;
  --btn-secondary: #7f8c8d;
  --btn-secondary-hover: #6c7a7d;
  --btn-warning: #e67e22;
  --btn-info: #3498db;

  /* ── Error ── */
  --error-bg: #fde8e8;
  --error-border: #e08080;
  --error-text: #b33;

  /* ── Success ── */
  --success-bg: #dcfce7;
  --success-border: #86efac;
  --success-text: #166534;

  /* ── Shadows ── */
  --shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.08);
  --shadow-md: 0 4px 12px rgba(0, 0, 0, 0.1);
  --shadow-lg: 0 10px 25px rgba(0, 0, 0, 0.12);

  /* ── Spacing ── */
  --radius-sm: 3px;
  --radius-md: 6px;
  --radius-lg: 10px;
}

/* ═══ Reset & Base ═══ */
*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;
  font-size: 13px;
  background: var(--bg-gradient);
  color: var(--text-main);
  padding: 20px;
  line-height: 1.5;
}

/* ═══ Typography ═══ */
h1, h2, h3, h4 {
  font-weight: 700;
  color: var(--primary-dark);
}

/* ═══ Government Header Bar ═══ */
.gov-header {
  background: var(--primary);
  color: var(--text-white);
  padding: 12px 20px;
  border-radius: var(--radius-md) var(--radius-md) 0 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.gov-header-logo {
  width: 42px;
  height: 42px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.gov-header-logo svg {
  width: 24px;
  height: 24px;
  fill: #fff;
}

.gov-header-text {
  flex: 1;
}

.gov-header-title {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.gov-header-subtitle {
  font-size: 11px;
  opacity: 0.85;
  margin-top: 2px;
}

/* ═══ Cards ═══ */
.card {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.card-header {
  background: var(--primary);
  color: var(--text-white);
  font-weight: 600;
  font-size: 13px;
  padding: 8px 14px;
  border-radius: var(--radius-md) var(--radius-md) 0 0;
}

.card-body {
  padding: 14px;
}

/* ═══ Buttons ═══ */
.btn {
  border: none;
  border-radius: var(--radius-sm);
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  transition: all 0.15s ease;
  text-decoration: none;
  line-height: 1.4;
}

.btn:hover {
  transform: translateY(-1px);
}

.btn:active {
  transform: translateY(0);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.btn-primary {
  background: var(--btn-primary);
  color: var(--text-white);
}

.btn-primary:hover:not(:disabled) {
  background: var(--btn-primary-hover);
}

.btn-success {
  background: var(--btn-success);
  color: var(--text-white);
}

.btn-success:hover:not(:disabled) {
  background: var(--btn-success-hover);
}

.btn-danger {
  background: var(--btn-danger);
  color: var(--text-white);
}

.btn-danger:hover:not(:disabled) {
  background: var(--btn-danger-hover);
}

.btn-secondary {
  background: var(--btn-secondary);
  color: var(--text-white);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--btn-secondary-hover);
}

.btn-warning {
  background: var(--btn-warning);
  color: var(--text-white);
}

.btn-info {
  background: var(--btn-info);
  color: var(--text-white);
}

.btn-outline {
  background: transparent;
  color: var(--primary);
  border: 1px solid var(--primary);
}

.btn-outline:hover:not(:disabled) {
  background: var(--primary);
  color: var(--text-white);
}

.btn-sm {
  padding: 3px 8px;
  font-size: 11px;
}

.btn-lg {
  padding: 10px 20px;
  font-size: 14px;
}

.btn-block {
  width: 100%;
  justify-content: center;
}

/* ═══ Form Controls ═══ */
.form-group {
  margin-bottom: 14px;
}

.form-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 4px;
}

.form-label.required::before {
  content: '* ';
  color: var(--error-text);
}

.form-control {
  width: 100%;
  height: 36px;
  padding: 6px 10px;
  font-family: inherit;
  font-size: 13px;
  color: var(--text-main);
  background: var(--bg-white);
  border: 1px solid var(--border-input);
  border-radius: var(--radius-sm);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.form-control:focus {
  border-color: var(--border-input-focus);
  box-shadow: 0 0 0 3px rgba(127, 168, 212, 0.15);
}

.form-control:disabled {
  background: #e2e8f0;
  color: #475569;
  cursor: not-allowed;
}

.form-control[readonly] {
  background: #f5f5f5;
  color: #333;
}

select.form-control {
  appearance: auto;
}

textarea.form-control {
  height: auto;
  resize: vertical;
}

/* ═══ Alerts ═══ */
.alert {
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.alert-error {
  background: var(--error-bg);
  border: 1px solid var(--error-border);
  color: var(--error-text);
}

.alert-success {
  background: var(--success-bg);
  border: 1px solid var(--success-border);
  color: var(--success-text);
}

.alert-warning {
  background: #fff8e1;
  border: 1px solid #f0d060;
  color: #8a6d00;
}

.alert-info {
  background: #e0f2fe;
  border: 1px solid #7dd3fc;
  color: #0369a1;
}

/* ═══ Tables ═══ */
.table-container {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

table th {
  background: var(--table-header-bg);
  color: var(--table-header-text);
  font-weight: 600;
  font-size: 12px;
  padding: 7px 8px;
  text-align: center;
  border: 1px solid var(--table-header-border);
  white-space: nowrap;
}

table td {
  padding: 5px 6px;
  border: 1px solid var(--table-border);
  text-align: center;
  vertical-align: middle;
  background: var(--table-row-odd);
}

table tbody tr:nth-child(even) td {
  background: var(--table-row-even);
}

table tbody tr:hover td {
  background: #cde4f4;
}

/* ═══ Status Badges ═══ */
.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.badge-draft {
  background: var(--status-draft-bg);
  color: var(--status-draft-text);
}

.badge-pending {
  background: var(--status-pending-bg);
  color: var(--status-pending-text);
}

.badge-approved {
  background: var(--status-approved-bg);
  color: var(--status-approved-text);
}

/* ═══ Layout Helpers ═══ */
.container {
  max-width: 1100px;
  margin: 0 auto;
}

.sheet-wrapper {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  border-radius: var(--radius-md);
  padding: 16px;
}

.flex {
  display: flex;
}

.flex-center {
  display: flex;
  align-items: center;
}

.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.flex-gap-sm {
  gap: 6px;
}

.flex-gap-md {
  gap: 10px;
}

.flex-gap-lg {
  gap: 16px;
}

.flex-wrap {
  flex-wrap: wrap;
}

.flex-1 {
  flex: 1;
}

.text-center {
  text-align: center;
}

.text-right {
  text-align: right;
}

.text-left {
  text-align: left;
}

.text-muted {
  color: var(--text-muted);
}

.text-white {
  color: var(--text-white);
}

.font-bold {
  font-weight: 700;
}

.mt-1 { margin-top: 8px; }
.mt-2 { margin-top: 14px; }
.mt-3 { margin-top: 20px; }
.mb-1 { margin-bottom: 8px; }
.mb-2 { margin-bottom: 14px; }
.mb-3 { margin-bottom: 20px; }

/* ═══ Modal ═══ */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  animation: fadeIn 0.15s ease;
}

.modal-content {
  background: var(--bg-white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  max-width: 440px;
  width: 90%;
  overflow: hidden;
  animation: slideUp 0.2s ease;
}

.modal-header {
  background: var(--primary);
  color: var(--text-white);
  padding: 12px 16px;
  font-weight: 600;
  font-size: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-body {
  padding: 20px 16px;
  font-size: 13px;
  line-height: 1.6;
}

.modal-footer {
  padding: 12px 16px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid #eee;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

/* ═══ Spinner ═══ */
.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  display: inline-block;
}

.spinner-dark {
  border-color: rgba(0, 0, 0, 0.1);
  border-top-color: var(--primary);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ═══ Loading Overlay ═══ */
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  border-radius: var(--radius-md);
}

/* ═══ Responsive ═══ */
@media (max-width: 768px) {
  body {
    padding: 10px;
  }

  .container {
    max-width: 100%;
  }

  .hide-mobile {
    display: none !important;
  }
}

@media print {
  .no-print {
    display: none !important;
  }

  body {
    background: white;
    padding: 0;
    color: #000;
  }

  .card {
    border: none;
    box-shadow: none;
  }
}
```

---

### 6.7 Frontend Markup (HTML Layouts)

#### File: `src/main/resources/static/login.html`
```html
<!DOCTYPE html>
<html lang="en" ng-app="hmwssbLoginApp">

<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>HMWSSB – Officer Portal Login</title>
  <link rel="stylesheet" href="styles/theme.css" />
  <script src="https://cdnjs.cloudflare.com/ajax/libs/angular.js/1.8.3/angular.min.js"></script>
  <script src="config.js"></script>
  <script src="app.js"></script>
  <style>
    body {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      padding: 20px;
      position: relative;
      overflow-x: hidden;
    }

    body::before, body::after {
      content: "";
      position: absolute;
      border-radius: 50%;
      background: linear-gradient(135deg, rgba(122, 122, 56, 0.1) 0%, rgba(90, 122, 58, 0.03) 100%);
      z-index: -1;
      animation: floatBubble 10s infinite ease-in-out;
    }

    body::before {
      width: 300px;
      height: 300px;
      top: -50px;
      left: -50px;
    }

    body::after {
      width: 400px;
      height: 400px;
      bottom: -100px;
      right: -100px;
      animation-delay: -5s;
    }

    @keyframes floatBubble {
      0%, 100% { transform: translateY(0) scale(1); }
      50% { transform: translateY(-30px) scale(1.05); }
    }

    .login-card {
      width: 100%;
      max-width: 420px;
      background: rgba(245, 245, 232, 0.95);
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
      border: 1px solid var(--border-card);
      border-radius: 12px;
      box-shadow: 0 15px 35px rgba(122, 122, 56, 0.12), 0 5px 15px rgba(0, 0, 0, 0.05);
      overflow: hidden;
      transition: transform 0.3s ease, box-shadow 0.3s ease;
    }

    .login-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 20px 40px rgba(122, 122, 56, 0.18), 0 8px 20px rgba(0, 0, 0, 0.06);
    }

    .login-header {
      background: var(--primary);
      color: #fff;
      padding: 24px 28px 20px;
      text-align: center;
    }

    .login-logo {
      display: inline-flex;
      justify-content: center;
      align-items: center;
      width: 56px;
      height: 56px;
      background: rgba(255, 255, 255, 0.15);
      border-radius: 50%;
      margin-bottom: 12px;
    }

    .login-logo svg {
      width: 28px;
      height: 28px;
      fill: #fff;
    }

    .login-title {
      font-size: 18px;
      font-weight: 700;
      letter-spacing: 0.3px;
    }

    .login-subtitle {
      font-size: 12px;
      opacity: 0.85;
      margin-top: 4px;
    }

    .login-body {
      padding: 28px;
    }

    .input-wrapper {
      position: relative;
    }

    .input-wrapper .toggle-password {
      position: absolute;
      right: 10px;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      border: none;
      cursor: pointer;
      color: var(--text-muted);
      font-size: 14px;
      padding: 4px;
    }

    .input-wrapper .toggle-password:hover {
      color: var(--primary);
    }

    .login-btn {
      width: 100%;
      height: 44px;
      background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
      color: #fff;
      border: none;
      border-radius: var(--radius-sm);
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 8px;
      box-shadow: 0 4px 12px rgba(122, 122, 56, 0.25);
      transition: all 0.2s ease;
      margin-top: 6px;
    }

    .login-btn:hover:not(:disabled) {
      background: linear-gradient(135deg, var(--primary-light) 0%, var(--primary) 100%);
      transform: translateY(-1px);
      box-shadow: 0 6px 15px rgba(122, 122, 56, 0.35);
    }

    .login-btn:disabled {
      background: #a0aec0;
      cursor: not-allowed;
      box-shadow: none;
      transform: none;
    }

    .login-footer {
      text-align: center;
      font-size: 11px;
      color: var(--text-muted);
      margin-top: 20px;
      line-height: 1.5;
    }

    .alert-shake {
      animation: shake 0.4s ease;
    }

    @keyframes shake {
      0%, 100% { transform: translateX(0); }
      20% { transform: translateX(-8px); }
      40% { transform: translateX(8px); }
      60% { transform: translateX(-4px); }
      80% { transform: translateX(4px); }
    }

    .phone-hint {
      font-size: 11px;
      color: var(--text-light);
      margin-top: 3px;
    }
  </style>
</head>

<body ng-controller="LoginCtrl">

  <div class="login-card">
    <div class="login-header">
      <div class="login-logo">
        <svg viewBox="0 0 24 24">
          <path d="M12,2C6.48,2,2,6.48,2,12s4.48,10,10,10,10-4.48,10-10S17.52,2,12,2zm-1,17.93c-3.95-.49-7-3.85-7-7.93,0-.62.08-1.21.21-1.79L9,15v1c0,1.1.9,2,2,2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55,0,1-.45,1-1V7h2c1.1,0,2-.9,2-2v-.41c2.93,1.19,5,4.06,5,7.41,0,2.08-.8,3.97-2.1,5.39z"/>
        </svg>
      </div>
      <div class="login-title">HMWSSB Portal</div>
      <div class="login-subtitle">Works Measurement &amp; Estimation System</div>
    </div>

    <div class="login-body">
      <div class="alert alert-error" ng-if="errorMessage" ng-class="{'alert-shake': shakeError}">
        <span>&#9888;</span> {{errorMessage}}
      </div>

      <div class="alert alert-success" ng-if="successMessage">
        <span>&#10003;</span> {{successMessage}}
      </div>

      <form ng-submit="executeLogin()" novalidate>
        <div class="form-group">
          <label class="form-label required" for="phone_input">Officer Phone Number</label>
          <div class="input-wrapper">
            <input type="tel" id="phone_input" class="form-control" ng-model="credentials.phoneNumber"
                   placeholder="e.g. 9876543210" required maxlength="10" pattern="[0-9]{10}"
                   autocomplete="username" />
          </div>
          <div class="phone-hint">Enter your 10-digit registered mobile number</div>
        </div>

        <div class="form-group">
          <label class="form-label required" for="password_input">Password</label>
          <div class="input-wrapper">
            <input ng-attr-type="{{showPassword ? 'text' : 'password'}}" id="password_input" class="form-control"
                   ng-model="credentials.password" placeholder="Enter your password" required
                   autocomplete="current-password" style="padding-right: 36px;" />
            <button type="button" class="toggle-password" ng-click="showPassword = !showPassword"
                    ng-bind="showPassword ? '&#128065;' : '&#128064;'" title="Toggle password visibility">
            </button>
          </div>
        </div>

        <button type="submit" class="login-btn" ng-disabled="submitting">
          <span class="spinner" ng-if="submitting"></span>
          <span ng-if="submitting">Authenticating...</span>
          <span ng-if="!submitting">Login to Account</span>
        </button>
      </form>

      <div class="login-footer">
        Government of Telangana<br/>
        Hyderabad Metropolitan Water Supply and Sewerage Board
      </div>
    </div>
  </div>

  <script>
    angular.module('hmwssbLoginApp', ['hmwssbShared'])
      .controller('LoginCtrl', ['$scope', '$http', 'AuthService',
        function ($scope, $http, AuthService) {

          if (AuthService.isLoggedIn()) {
            window.location.href = 'index.html';
            return;
          }

          $scope.credentials = { phoneNumber: '', password: '' };
          $scope.errorMessage = null;
          $scope.successMessage = null;
          $scope.submitting = false;
          $scope.showPassword = false;
          $scope.shakeError = false;

          $scope.executeLogin = function () {
            $scope.errorMessage = null;
            $scope.successMessage = null;
            $scope.shakeError = false;

            if (!$scope.credentials.phoneNumber || !$scope.credentials.phoneNumber.trim()) {
              $scope.errorMessage = 'Please enter your phone number.';
              $scope.shakeError = true;
              return;
            }
            if (!$scope.credentials.password || !$scope.credentials.password.trim()) {
              $scope.errorMessage = 'Please enter your password.';
              $scope.shakeError = true;
              return;
            }

            $scope.submitting = true;

            $http.post(APP_CONFIG.API_BASE + '/users/login', {
              phoneNumber: $scope.credentials.phoneNumber.trim(),
              password: $scope.credentials.password
            }).then(function (response) {
              $scope.submitting = false;
              AuthService.setUser(response.data);
              $scope.successMessage = 'Login successful! Redirecting...';
              setTimeout(function () {
                window.location.href = 'index.html';
              }, 500);
            }, function (error) {
              $scope.submitting = false;
              $scope.shakeError = true;
              if (error.status === 401 || error.status === 400 || error.status === 404) {
                $scope.errorMessage = error.data || 'Invalid phone number or password. Please try again.';
              } else {
                $scope.errorMessage = 'Could not reach the server. Please make sure the application is running.';
              }
            });
          };
        }
      ]);
  </script>
</body>

</html>
```

#### File: `src/main/resources/static/index.html`
```html
<!DOCTYPE html>
<html lang="en" ng-app="hmwssbApp">

<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>HMWSSB – Measurement Sheet</title>
  <link rel="stylesheet" href="styles/theme.css" />
  <script src="https://cdnjs.cloudflare.com/ajax/libs/angular.js/1.8.3/angular.min.js"></script>
  <script src="config.js"></script>
  <script src="app.js"></script>
  <script src="hierarchy.js"></script>
  <style>
    /* ── Government Header ── */
    .page-header {
      background: var(--primary);
      color: var(--text-white);
      padding: 10px 20px;
      border-radius: var(--radius-md) var(--radius-md) 0 0;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .page-header-left {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .page-header-logo {
      width: 36px;
      height: 36px;
      background: rgba(255, 255, 255, 0.15);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .page-header-logo svg {
      width: 20px;
      height: 20px;
      fill: #fff;
    }

    .page-header-title {
      font-size: 13px;
      font-weight: 700;
    }

    .page-header-sub {
      font-size: 10px;
      opacity: 0.8;
    }

    /* ── User Bar ── */
    .user-bar {
      background: var(--bg-highlight);
      border: 1px solid var(--border-light);
      border-top: none;
      padding: 8px 16px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .user-avatar {
      width: 32px;
      height: 32px;
      background: var(--primary);
      color: #fff;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      font-weight: 700;
      flex-shrink: 0;
    }

    .user-name {
      font-weight: 700;
      font-size: 13px;
      color: #2c3e50;
    }

    .user-designation {
      font-size: 11px;
      color: var(--text-light);
    }

    .location-select {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .location-select label {
      font-weight: 600;
      color: var(--error-text);
      font-size: 11px;
      white-space: nowrap;
    }

    .location-select select {
      border: 1px solid var(--border-input);
      border-radius: var(--radius-sm);
      padding: 3px 8px;
      font-size: 11px;
      height: 26px;
      background: #fff;
      outline: none;
      max-width: 380px;
    }

    /* ── Workflow Tracker ── */
    .workflow-bar {
      background: var(--bg-highlight);
      border: 1px solid var(--border-light);
      border-radius: var(--radius-md);
      padding: 10px 14px;
      margin-bottom: 14px;
      display: flex;
      align-items: center;
      gap: 6px;
      overflow-x: auto;
    }

    .workflow-label {
      font-weight: 700;
      color: var(--primary);
      font-size: 10px;
      text-transform: uppercase;
      white-space: nowrap;
      margin-right: 8px;
    }

    .workflow-steps {
      display: flex;
      align-items: center;
      gap: 4px;
      flex: 1;
      justify-content: space-around;
      min-width: 580px;
    }

    .wf-step {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 3px;
    }

    .wf-step-circle {
      width: 22px;
      height: 22px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 10px;
      font-weight: 700;
      border: 2px solid #cbd5e1;
      background: #e2e8f0;
      color: #64748b;
      transition: all 0.3s ease;
    }

    .wf-step-circle.active {
      background: var(--primary);
      color: #fff;
      border-color: var(--primary);
      box-shadow: 0 0 0 3px rgba(122, 122, 56, 0.2);
    }

    .wf-step-circle.completed {
      background: var(--btn-success);
      color: #fff;
      border-color: var(--btn-success);
    }

    .wf-step-label {
      font-size: 9px;
      font-weight: 600;
      color: #64748b;
      text-align: center;
      white-space: nowrap;
    }

    .wf-step-label.active {
      color: var(--primary);
    }

    .wf-step-label.completed {
      color: var(--btn-success);
    }

    .wf-arrow {
      color: #cbd5e1;
      font-weight: 700;
      font-size: 12px;
      margin: 0 2px;
    }

    /* ── Hierarchy Section ── */
    .hierarchy-grid {
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      gap: 10px;
      background: var(--bg-highlight);
      border: 1px solid var(--border-light);
      border-radius: var(--radius-sm);
      padding: 10px 12px;
      margin-bottom: 12px;
    }

    @media (max-width: 768px) {
      .hierarchy-grid {
        grid-template-columns: 1fr;
      }
    }

    .hierarchy-field label {
      display: block;
      font-weight: 600;
      color: var(--error-text);
      font-size: 12px;
      margin-bottom: 3px;
    }

    .hierarchy-field label::before {
      content: '* ';
    }

    .hierarchy-field select {
      width: 100%;
      border: 1px solid var(--border-input);
      border-radius: var(--radius-sm);
      padding: 4px 8px;
      font-size: 12px;
      background: #fff;
      height: 28px;
    }

    /* ── Name of Work ── */
    .name-of-work {
      display: flex;
      align-items: center;
      gap: 10px;
      background: var(--bg-highlight);
      border: 1px solid var(--border-light);
      border-radius: var(--radius-sm);
      padding: 8px 12px;
      margin-bottom: 12px;
    }

    .name-of-work label {
      font-weight: 600;
      color: var(--error-text);
      font-size: 12px;
      white-space: nowrap;
    }

    .name-of-work label::before {
      content: '* ';
    }

    .name-of-work input {
      flex: 1;
      border: 1px solid var(--border-input);
      border-radius: var(--radius-sm);
      padding: 5px 10px;
      font-size: 12px;
      outline: none;
    }

    .name-of-work input:focus {
      border-color: var(--border-input-focus);
    }

    /* ── Measurement Table ── */
    .measure-table-wrap {
      overflow-x: auto;
    }

    .measure-table {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
      min-width: 900px;
    }

    .measure-table th {
      background: var(--table-header-bg);
      color: var(--table-header-text);
      font-weight: 600;
      font-size: 11px;
      padding: 6px 4px;
      text-align: center;
      border: 1px solid var(--table-header-border);
      white-space: nowrap;
    }

    .measure-table td {
      padding: 4px 3px;
      border: 1px solid var(--table-border);
      text-align: center;
      background: var(--table-row-odd);
      vertical-align: middle;
    }

    .measure-table tbody tr:nth-child(even) td {
      background: var(--table-row-even);
    }

    .measure-table tbody tr:hover td {
      background: #cde4f4;
    }

    .measure-table td input[type="number"],
    .measure-table td input[type="text"] {
      width: 100%;
      border: 1px solid #bbb;
      border-radius: var(--radius-sm);
      padding: 3px 4px;
      font-size: 11px;
      text-align: center;
      background: #fff;
      outline: none;
    }

    .measure-table td input[type="text"].desc-input {
      text-align: left;
    }

    .measure-table td input:focus {
      border-color: var(--border-input-focus);
    }

    .measure-table td input[readonly] {
      background: #e2e8f0;
      color: #475569;
      border: 1px solid #cbd5e1;
      cursor: not-allowed;
    }

    .radio-group {
      display: flex;
      gap: 8px;
      justify-content: center;
      align-items: center;
      white-space: nowrap;
    }

    .radio-group label {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      cursor: pointer;
      font-weight: 500;
      font-size: 11px;
    }

    .row-total-bar td {
      background: var(--primary) !important;
      color: #fff;
      font-weight: 600;
      font-size: 12px;
      padding: 6px 10px;
      text-align: right;
      border: 1px solid var(--primary-dark);
    }

    /* ── Column widths ── */
    .col-sno { width: 4%; }
    .col-ismat { width: 8%; }
    .col-desc { width: 22%; }
    .col-nums { width: 7%; }
    .col-len { width: 7%; }
    .col-bre { width: 7%; }
    .col-dep { width: 6%; }
    .col-qty { width: 7%; }
    .col-rate { width: 7%; }
    .col-unit { width: 6%; }
    .col-amt { width: 8%; }
    .col-act { width: 9%; }

    /* ── Totals Panel ── */
    .totals-panel {
      margin-top: 12px;
      background: var(--bg-highlight);
      border: 1px solid var(--border-light);
      border-radius: var(--radius-sm);
      overflow: hidden;
    }

    .totals-header {
      background: var(--primary);
      color: #fff;
      font-weight: 600;
      font-size: 12px;
      padding: 6px 14px;
    }

    .totals-body {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      padding: 10px 14px;
      align-items: center;
    }

    .total-field {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .total-field label {
      font-size: 12px;
      color: var(--text-muted);
      white-space: nowrap;
      font-weight: 500;
    }

    .total-field input {
      width: 130px;
      border: 1px solid #bbb;
      border-radius: var(--radius-sm);
      padding: 4px 8px;
      font-size: 12px;
      text-align: right;
      background: #fff;
    }

    .total-field input[readonly] {
      background: #f5f5f5;
    }

    .total-field select {
      border: 1px solid #bbb;
      border-radius: var(--radius-sm);
      padding: 4px 8px;
      font-size: 12px;
      background: #fff;
      width: 70px;
    }

    /* ── Saved Estimates ── */
    .estimates-panel {
      margin-top: 16px;
    }

    .estimates-header {
      background: #2c3e50;
      color: #fff;
      font-weight: 600;
      font-size: 12px;
      padding: 6px 14px;
      border-radius: var(--radius-md) var(--radius-md) 0 0;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .estimates-body {
      padding: 10px;
      background: var(--bg-highlight);
      max-height: 220px;
      overflow-y: auto;
    }

    .estimates-table {
      width: 100%;
      border-collapse: collapse;
    }

    .estimates-table th {
      background: #d5dbdb;
      color: #2c3e50;
      border: 1px solid #bdc3c7;
      padding: 5px 4px;
      font-size: 11px;
      font-weight: 600;
    }

    .estimates-table td {
      border: 1px solid #bdc3c7;
      background: #fff;
      padding: 5px 6px;
      font-size: 12px;
    }

    .estimates-table tr:hover td {
      background: #f0f7ff;
    }

    /* ── Autocomplete ── */
    .desc-wrapper {
      position: relative;
      width: 100%;
    }

    .global-suggestions {
      position: fixed;
      background: rgba(255, 255, 255, 0.98);
      backdrop-filter: blur(8px);
      border: 1px solid #a4c2e6;
      border-radius: var(--radius-md);
      box-shadow: var(--shadow-lg);
      z-index: 9999;
      display: none;
      padding: 6px;
      max-height: 260px;
      overflow-y: auto;
    }

    .suggestions-list {
      list-style: none;
      padding: 0;
      margin: 0;
    }

    .suggestions-list li {
      padding: 8px 10px;
      cursor: pointer;
      font-size: 12px;
      border-bottom: 1px solid #f1f5f9;
      transition: background 0.1s;
    }

    .suggestions-list li:last-child {
      border-bottom: none;
    }

    .suggestions-list li:hover,
    .suggestions-list li.active {
      background: #f0f7ff;
    }

    .search-loading {
      padding: 12px;
      text-align: center;
      font-size: 12px;
      color: var(--text-light);
    }

    .no-results {
      padding: 12px;
      text-align: center;
      font-size: 12px;
      color: var(--error-text);
      font-weight: 500;
    }
  </style>
</head>

<body ng-controller="MeasurementCtrl" ng-click="closeAllSuggestions()">

  <div class="sheet-wrapper">

    <!-- Government Header -->
    <div class="page-header">
      <div class="page-header-left">
        <div class="page-header-logo">
          <svg viewBox="0 0 24 24"><path d="M12,2C6.48,2,2,6.48,2,12s4.48,10,10,10,10-4.48,10-10S17.52,2,12,2zm-1,17.93c-3.95-.49-7-3.85-7-7.93,0-.62.08-1.21.21-1.79L9,15v1c0,1.1.9,2,2,2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55,0,1-.45,1-1V7h2c1.1,0,2-.9,2-2v-.41c2.93,1.19,5,4.06,5,7.41,0,2.08-.8,3.97-2.1,5.39z"/></svg>
        </div>
        <div>
          <div class="page-header-title">HMWSSB – Works Measurement Sheet</div>
          <div class="page-header-sub">Hyderabad Metropolitan Water Supply and Sewerage Board</div>
        </div>
      </div>
      <button class="btn btn-danger btn-sm" ng-click="logout()">Logout</button>
    </div>

    <!-- User Bar -->
    <div class="user-bar">
      <div class="user-info">
        <div class="user-avatar">{{currentUser.name.charAt(0)}}</div>
        <div>
          <div class="user-name">{{currentUser.name}}</div>
          <div class="user-designation">{{currentUser.designation}} &bull; {{currentUser.phoneNumber}}</div>
        </div>
      </div>
      <div class="location-select" ng-if="locationOptions.length > 0">
        <label>Access Location:</label>
        <select ng-model="selectedLocationKey" ng-change="onLocationChange()">
          <option ng-repeat="opt in locationOptions" ng-value="opt.key">
            {{opt.label}}
          </option>
        </select>
      </div>
    </div>

    <!-- Workflow Tracker -->
    <div class="workflow-bar mt-2">
      <div class="workflow-label">Workflow:</div>
      <div class="workflow-steps">
        <div class="wf-step">
          <div class="wf-step-circle" ng-class="getStepClass('DRAFT')">1</div>
          <div class="wf-step-label" ng-class="getStepLabelClass('DRAFT')">Draft (AE)</div>
        </div>
        <span class="wf-arrow">&#10132;</span>
        <div class="wf-step">
          <div class="wf-step-circle" ng-class="getStepClass('SUBMITTED_TO_DGM')">2</div>
          <div class="wf-step-label" ng-class="getStepLabelClass('SUBMITTED_TO_DGM')">DGM Scrutiny</div>
        </div>
        <span class="wf-arrow">&#10132;</span>
        <div class="wf-step">
          <div class="wf-step-circle" ng-class="getStepClass('SUBMITTED_TO_GM')">3</div>
          <div class="wf-step-label" ng-class="getStepLabelClass('SUBMITTED_TO_GM')">GM Recommend</div>
        </div>
        <span class="wf-arrow">&#10132;</span>
        <div class="wf-step">
          <div class="wf-step-circle" ng-class="getStepClass('SUBMITTED_TO_CGM')">4</div>
          <div class="wf-step-label" ng-class="getStepLabelClass('SUBMITTED_TO_CGM')">CGM Review</div>
        </div>
        <span class="wf-arrow">&#10132;</span>
        <div class="wf-step">
          <div class="wf-step-circle" ng-class="getStepClass('SUBMITTED_TO_DOP')">5</div>
          <div class="wf-step-label" ng-class="getStepLabelClass('SUBMITTED_TO_DOP')">DOP Sanction</div>
        </div>
        <span class="wf-arrow">&#10132;</span>
        <div class="wf-step">
          <div class="wf-step-circle" ng-class="getStepClass('APPROVED')">&#10003;</div>
          <div class="wf-step-label" ng-class="getStepLabelClass('APPROVED')">Approved</div>
        </div>
      </div>
    </div>

    <!-- API Error -->
    <div class="alert alert-error" ng-if="apiError">{{apiError}}</div>

    <!-- Name of Work -->
    <div class="name-of-work">
      <label>Name Of Work</label>
      <input type="text" ng-model="nameOfWork" placeholder="Enter name of work" ng-disabled="!isEditable()" />
    </div>

    <!-- Hierarchy Selection -->
    <div class="hierarchy-grid">
      <div class="hierarchy-field">
        <label>CORP</label>
        <select ng-model="selectedCorp" ng-change="onCorpChange()" ng-options="c for c in corps" ng-disabled="!isEditable()">
          <option value="">-- Select --</option>
        </select>
      </div>
      <div class="hierarchy-field">
        <label>Zone Name</label>
        <select ng-model="selectedZone" ng-change="onZoneChange()" ng-options="z for z in zones" ng-disabled="!selectedCorp || !isEditable()">
          <option value="">-- Select --</option>
        </select>
      </div>
      <div class="hierarchy-field">
        <label>DIVISION</label>
        <select ng-model="selectedDivision" ng-change="onDivisionChange()" ng-options="d for d in divisions" ng-disabled="!selectedZone || !isEditable()">
          <option value="">-- Select --</option>
        </select>
      </div>
      <div class="hierarchy-field">
        <label>Circle</label>
        <select ng-model="selectedCircle" ng-change="onCircleChange()" ng-options="cir for cir in circles" ng-disabled="!selectedDivision || !isEditable()">
          <option value="">-- Select --</option>
        </select>
      </div>
      <div class="hierarchy-field">
        <label>Ward</label>
        <select ng-model="selectedWard" ng-options="w for w in wards" ng-disabled="!selectedCircle || !isEditable()">
          <option value="">-- Select --</option>
        </select>
      </div>
    </div>

    <!-- Measurement Table -->
    <div class="measure-table-wrap">
      <table class="measure-table">
        <colgroup>
          <col class="col-sno"><col class="col-ismat"><col class="col-desc">
          <col class="col-nums"><col class="col-len"><col class="col-bre">
          <col class="col-dep"><col class="col-qty"><col class="col-rate">
          <col class="col-unit"><col class="col-amt"><col class="col-act">
        </colgroup>
        <thead>
          <tr>
            <th>S.No</th><th>Material</th><th>Description</th>
            <th>No</th><th>Length</th><th>Breadth</th>
            <th>Depth</th><th>Qty</th><th>Rate</th>
            <th>Unit</th><th>Amount</th><th>Action</th>
          </tr>
        </thead>
        <tbody>
          <tr ng-repeat="row in rows">
            <td>{{$index + 1}}</td>
            <td>
              <div class="radio-group">
                <label><input type="radio" name="mat_{{$index}}" ng-model="row.isMaterial" value="Yes" ng-disabled="!isEditable()" /> Yes</label>
                <label><input type="radio" name="mat_{{$index}}" ng-model="row.isMaterial" value="No" ng-disabled="!isEditable()" /> No</label>
              </div>
            </td>
            <td ng-click="$event.stopPropagation()">
              <div class="desc-wrapper">
                <input class="desc-input" type="text" ng-model="row.description"
                  ng-keydown="onDescriptionKeydown($event, row)" ng-keyup="onDescriptionKeyup($event, row)"
                  ng-focus="onDescriptionFocus($event, row)" placeholder="Type to search..." autocomplete="off" ng-disabled="!isEditable()" />
              </div>
            </td>
            <td><input type="number" ng-model="row.num" ng-change="computeRow(row)" min="1" ng-disabled="!isEditable()" /></td>
            <td><input type="number" ng-model="row.length" ng-change="computeRow(row)" min="0" step="0.01" ng-disabled="!isEditable()" /></td>
            <td><input type="number" ng-model="row.breadth" ng-change="computeRow(row)" min="0" step="0.01" ng-disabled="!isEditable()" /></td>
            <td><input type="number" ng-model="row.depth" ng-change="computeRow(row)" min="0" step="0.01" ng-disabled="!isEditable()" /></td>
            <td><input type="number" ng-model="row.quantity" readonly /></td>
            <td><input type="number" ng-model="row.rate" readonly /></td>
            <td><input type="text" ng-model="row.unit" readonly /></td>
            <td class="font-bold">{{row.amount | number:2}}</td>
            <td style="white-space:nowrap;">
              <button class="btn btn-success btn-sm" ng-click="addRow()" ng-if="isEditable()">Add</button>
              <button class="btn btn-danger btn-sm" ng-click="removeRow($index)" ng-if="isEditable() && rows.length > 1">Del</button>
              <span ng-if="!isEditable()" class="text-muted" style="font-style:italic;font-size:10px;">Read-Only</span>
            </td>
          </tr>
          <tr class="row-total-bar">
            <td colspan="11">Total &nbsp;&nbsp; {{rowTotal() | number:2}}</td>
            <td></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Totals Panel -->
    <div class="totals-panel">
      <div class="totals-header">Totals</div>
      <div class="totals-body">
        <div class="total-field">
          <label>Total Amount</label>
          <input type="text" readonly ng-value="rowTotal().toFixed(2)" />
        </div>
        <div class="total-field">
          <label>GST</label>
          <select ng-model="gstPercent" ng-change="computeTotals()" ng-disabled="!isEditable()">
            <option value="0">0%</option>
            <option value="5">5%</option>
            <option value="12">12%</option>
            <option value="18">18%</option>
          </select>
          <input type="text" readonly ng-value="gstAmount().toFixed(2)" />
        </div>
        <div class="total-field">
          <label>Grand Total</label>
          <input type="text" readonly ng-value="grandTotal().toFixed(2)" />
        </div>
        <div style="margin-left:auto;display:flex;gap:6px;">
          <button class="btn btn-primary" ng-click="saveEstimate()" ng-disabled="savingEstimate">
            <span ng-if="savingEstimate"><span class="spinner"></span></span>
            <span ng-if="!savingEstimate && isEditable()">Generate Estimate</span>
            <span ng-if="!savingEstimate && !isEditable()">View Abstract</span>
          </button>
          <button class="btn btn-secondary" ng-click="resetForm()" ng-if="currentUser.role === 'MANAGER'">New</button>
        </div>
      </div>
    </div>

    <!-- Saved Estimates -->
    <div class="estimates-panel">
      <div class="estimates-header">
        <span>Saved Estimates</span>
        <div style="display:flex;gap:6px;align-items:center;">
          <select ng-model="filterStatus" ng-change="loadSavedEstimates()" style="border:1px solid rgba(255,255,255,0.3);background:transparent;color:#fff;border-radius:var(--radius-sm);padding:2px 6px;font-size:11px;">
            <option value="">All Status</option>
            <option value="DRAFT">Draft</option>
            <option value="SUBMITTED_TO_DGM">Pending DGM</option>
            <option value="SUBMITTED_TO_GM">Pending GM</option>
            <option value="SUBMITTED_TO_CGM">Pending CGM</option>
            <option value="SUBMITTED_TO_DOP">Pending DOP</option>
            <option value="APPROVED">Approved</option>
          </select>
          <button class="btn btn-sm" style="background:transparent;border:1px solid rgba(255,255,255,0.4);color:#fff;"
                  ng-click="loadSavedEstimates()">Refresh</button>
        </div>
      </div>
      <div class="estimates-body">
        <!-- Summary View for GM/CGM/DOP -->
        <div ng-if="summaryData.length > 0 && !selectedSummaryKey" style="margin-bottom:12px;">
          <div style="font-weight:700;font-size:12px;color:#2c3e50;margin-bottom:6px;">Summary by {{summaryGroupBy}} (click to drill down)</div>
          <table class="estimates-table">
            <thead>
              <tr>
                <th style="width:30%">{{summaryGroupBy}}</th>
                <th style="width:12%">Total</th>
                <th style="width:15%">Draft</th>
                <th style="width:15%">Pending</th>
                <th style="width:15%">Approved</th>
                <th style="width:13%">Value</th>
              </tr>
            </thead>
            <tbody>
              <tr ng-repeat="s in summaryData" ng-click="selectSummaryRow(s.key)" style="cursor:pointer;">
                <td class="font-bold" style="color:#2c3e50;">{{s.key}}</td>
                <td class="text-center">{{s.total}}</td>
                <td class="text-center"><span class="badge badge-draft">{{s.draft}}</span></td>
                <td class="text-center"><span class="badge badge-pending">{{s.pending}}</span></td>
                <td class="text-center"><span class="badge badge-approved">{{s.approved}}</span></td>
                <td class="text-right font-bold" style="color:#27ae60;">{{s.grandTotal | number:2}}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Drilldown View -->
        <div ng-if="selectedSummaryKey" style="margin-bottom:12px;">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;">
            <button class="btn btn-sm btn-secondary" ng-click="clearSummaryDrilldown()">&#8592; Back to Summary</button>
            <span style="font-weight:700;font-size:12px;color:#2c3e50;">{{selectedSummaryKey}} — {{drilldownEstimates.length}} estimates</span>
          </div>
        </div>

        <table class="estimates-table">
          <thead>
            <tr>
              <th style="width:8%">ID</th>
              <th style="width:35%">Name Of Work</th>
              <th style="width:14%">Total</th>
              <th style="width:14%">Status</th>
              <th style="width:16%">Date</th>
              <th style="width:13%">Action</th>
            </tr>
          </thead>
          <tbody>
            <tr ng-repeat="est in (selectedSummaryKey ? drilldownEstimates : savedEstimates)">
              <td class="text-center">{{est.id}}</td>
              <td class="font-bold" style="color:#2c3e50;">{{est.nameOfWork || 'Unnamed Work'}}</td>
              <td class="text-right font-bold" style="color:#27ae60;">{{est.grandTotal | number:2}}</td>
              <td class="text-center">
                <span class="badge" ng-class="getStatusBadgeClass(est.status)">{{getStatusLabel(est.status)}}</span>
              </td>
              <td class="text-center text-muted">{{formatDate(est.createdAt)}}</td>
              <td class="text-center">
                <button class="btn btn-primary btn-sm" ng-click="loadEstimate(est.id)">Load</button>
                <button class="btn btn-danger btn-sm" ng-click="deleteEstimate(est.id)"
                        ng-if="currentUser.role === 'MANAGER' && (!est.status || est.status === 'DRAFT')">Del</button>
              </td>
            </tr>
            <tr ng-if="savedEstimates.length === 0">
              <td colspan="6" class="text-center text-muted" style="font-style:italic;padding:12px;">No saved estimates found.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

  </div>

  <script>
    angular.module('hmwssbApp', ['hmwssbShared'])
      .controller('MeasurementCtrl', ['$scope', '$http', '$timeout', 'AuthService', 'StatusService', 'ModalService', 'Utils',
        function ($scope, $http, $timeout, AuthService, StatusService, ModalService, Utils) {

          if (!AuthService.requireLogin()) return;
          $scope.currentUser = AuthService.getUser();
          $scope.currentEstimateStatus = 'DRAFT';

          // Signature fields
          $scope.currentPreparedByName = null;
          $scope.currentPreparedByDesignation = null;
          $scope.currentVerifiedByName = null;
          $scope.currentVerifiedByDesignation = null;
          $scope.currentRecommendedByName = null;
          $scope.currentRecommendedByDesignation = null;
          $scope.currentForwardedByName = null;
          $scope.currentForwardedByDesignation = null;
          $scope.currentSanctionedByName = null;
          $scope.currentSanctionedByDesignation = null;

          $scope.isEditable = function () {
            return StatusService.isEditable($scope.currentEstimateStatus, $scope.currentUser.role);
          };

          $scope.getStepClass = function (stepStatus) {
            var active = $scope.currentEstimateStatus || 'DRAFT';
            if (active === stepStatus) return 'active';
            var order = StatusService.getStatusOrder();
            if (order.indexOf(active) > order.indexOf(stepStatus)) return 'completed';
            return '';
          };

          $scope.getStepLabelClass = function (stepStatus) {
            var active = $scope.currentEstimateStatus || 'DRAFT';
            if (active === stepStatus) return 'active';
            var order = StatusService.getStatusOrder();
            if (order.indexOf(active) > order.indexOf(stepStatus)) return 'completed';
            return '';
          };

          $scope.getStatusLabel = StatusService.getLabel;
          $scope.getStatusBadgeClass = StatusService.getBadgeClass;
          $scope.formatDate = Utils.formatDate;

          $scope.logout = function () { AuthService.logout(); };

          $scope.locationOptions = [];
          $scope.selectedLocationKey = '';

          function buildLocationOptions() {
            var role = $scope.currentUser.role;
            var locs = $scope.currentUser.locations || [];
            var options = [];

            if (role === 'MANAGER') {
              locs.forEach(function(loc, i) {
                if (loc.wardName) {
                  options.push({
                    key: 'ward_' + i,
                    label: loc.circleName + ' \u2014 ' + loc.wardName,
                    corp: loc.corp, zone: loc.zoneName, division: loc.division,
                    circle: loc.circleName, ward: loc.wardName, filterType: 'ward'
                  });
                }
              });
              if (options.length === 0) {
                locs.forEach(function(loc, i) {
                  options.push({
                    key: 'loc_' + i,
                    label: (loc.circleName || 'Unknown') + (loc.wardName ? ' \u2014 ' + loc.wardName : ''),
                    corp: loc.corp, zone: loc.zoneName, division: loc.division,
                    circle: loc.circleName, ward: loc.wardName || '', filterType: 'circle'
                  });
                });
              }
            } else if (role === 'DGM') {
              var userCircle = null, userCorp = null, userZone = null, userDiv = null;
              for (var i = 0; i < locs.length; i++) {
                if (locs[i].circleName) { userCircle = locs[i].circleName; userCorp = locs[i].corp; userZone = locs[i].zoneName; userDiv = locs[i].division; break; }
              }
              if (userCircle) {
                options.push({
                  key: 'circle_all',
                  label: 'All Wards in ' + userCircle,
                  corp: userCorp, zone: userZone, division: userDiv,
                  circle: userCircle, ward: '', filterType: 'circle'
                });
                var wardsInCircle = getWardsForCircle(userCorp, userZone, userDiv, userCircle);
                wardsInCircle.forEach(function(ward) {
                  options.push({
                    key: 'ward_' + ward,
                    label: '  \u2514 ' + ward,
                    corp: userCorp, zone: userZone, division: userDiv,
                    circle: userCircle, ward: ward, filterType: 'ward'
                  });
                });
              }
            } else if (role === 'GM') {
              var divisions = [];
              locs.forEach(function(loc) {
                if (loc.division && divisions.indexOf(loc.division) === -1) divisions.push(loc.division);
              });
              var userCorp = null, userZone = null;
              for (var i = 0; i < locs.length; i++) {
                if (locs[i].corp) { userCorp = locs[i].corp; userZone = locs[i].zoneName; break; }
              }
              divisions.forEach(function(div) {
                options.push({
                  key: 'div_' + div,
                  label: 'All Wards in Division ' + div,
                  corp: userCorp, zone: userZone, division: div,
                  circle: '', ward: '', filterType: 'division'
                });
                var circlesInDiv = getCirclesForDivision(userCorp, userZone, div);
                circlesInDiv.forEach(function(cir) {
                  options.push({
                    key: 'cir_' + div + '_' + cir,
                    label: '  \u2514 ' + cir,
                    corp: userCorp, zone: userZone, division: div,
                    circle: cir, ward: '', filterType: 'circle'
                  });
                });
              });
            } else if (role === 'CGM') {
              var userCorp = null, userZone = null;
              for (var i = 0; i < locs.length; i++) {
                if (locs[i].zoneName) { userCorp = locs[i].corp; userZone = locs[i].zoneName; break; }
              }
              if (userZone) {
                options.push({
                  key: 'zone_all',
                  label: 'All Wards in ' + userZone,
                  corp: userCorp, zone: userZone, division: '',
                  circle: '', ward: '', filterType: 'zone'
                });
                var divsInZone = getDivisionsForZone(userCorp, userZone);
                divsInZone.forEach(function(div) {
                  options.push({
                    key: 'div_' + div,
                    label: '  \u2514 Division ' + div,
                    corp: userCorp, zone: userZone, division: div,
                    circle: '', ward: '', filterType: 'division'
                  });
                });
              }
            }

            $scope.locationOptions = options;
            if (options.length > 0) {
              $scope.selectedLocationKey = options[0].key;
              onLocationSelect(options[0]);
            }
          }

          function getWardsForCircle(corp, zone, div, circle) {
            if (!WARD_HIERARCHY[corp] || !WARD_HIERARCHY[corp][zone]) return [];
            if (!WARD_HIERARCHY[corp][zone][div]) return [];
            return WARD_HIERARCHY[corp][zone][div][circle] || [];
          }

          function getCirclesForDivision(corp, zone, div) {
            if (!WARD_HIERARCHY[corp] || !WARD_HIERARCHY[corp][zone]) return [];
            if (!WARD_HIERARCHY[corp][zone][div]) return [];
            return Object.keys(WARD_HIERARCHY[corp][zone][div]);
          }

          function getDivisionsForZone(corp, zone) {
            if (!WARD_HIERARCHY[corp] || !WARD_HIERARCHY[corp][zone]) return [];
            return Object.keys(WARD_HIERARCHY[corp][zone]);
          }

          function onLocationSelect(opt) {
            populateHierarchy(opt.corp, opt.zone, opt.division, opt.circle, opt.ward);
            $scope.filterWard = opt.ward || '';
            $scope.filterCircle = opt.circle || '';
            $scope.filterDivision = opt.division || '';
            $scope.filterZone = opt.zone || '';
            $scope.loadSavedEstimates();
          }

          $scope.onLocationChange = function() {
            var opt = $scope.locationOptions.find(function(o) { return o.key === $scope.selectedLocationKey; });
            if (opt) onLocationSelect(opt);
          };

          var API_BASE = APP_CONFIG.API_BASE;
          var SEARCH_DEBOUNCE_MS = APP_CONFIG.SEARCH_DEBOUNCE_MS;
          var MIN_SEARCH_LENGTH = APP_CONFIG.MIN_SEARCH_LENGTH;

          $scope.nameOfWork = '';
          $scope.gstPercent = '0';
          $scope.apiError = null;
          var searchTimers = {};

          /* ── Hierarchy ── */
          $scope.hierarchyData = WARD_HIERARCHY;
          $scope.corps = Object.keys($scope.hierarchyData);
          $scope.zones = []; $scope.divisions = []; $scope.circles = []; $scope.wards = [];
          $scope.selectedCorp = ''; $scope.selectedZone = ''; $scope.selectedDivision = '';
          $scope.selectedCircle = ''; $scope.selectedWard = '';

          $scope.onCorpChange = function () {
            $scope.selectedZone = ''; $scope.selectedDivision = ''; $scope.selectedCircle = ''; $scope.selectedWard = '';
            $scope.zones = []; $scope.divisions = []; $scope.circles = []; $scope.wards = [];
            if ($scope.selectedCorp && $scope.hierarchyData[$scope.selectedCorp]) {
              $scope.zones = Object.keys($scope.hierarchyData[$scope.selectedCorp]);
            }
          };

          $scope.onZoneChange = function () {
            $scope.selectedDivision = ''; $scope.selectedCircle = ''; $scope.selectedWard = '';
            $scope.divisions = []; $scope.circles = []; $scope.wards = [];
            if ($scope.selectedCorp && $scope.selectedZone && $scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone]) {
              $scope.divisions = Object.keys($scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone]);
            }
          };

          $scope.onDivisionChange = function () {
            $scope.selectedCircle = ''; $scope.selectedWard = '';
            $scope.circles = []; $scope.wards = [];
            if ($scope.selectedCorp && $scope.selectedZone && $scope.selectedDivision &&
                $scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone][$scope.selectedDivision]) {
              $scope.circles = Object.keys($scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone][$scope.selectedDivision]);
            }
          };

          $scope.onCircleChange = function () {
            $scope.selectedWard = ''; $scope.wards = [];
            if ($scope.selectedCorp && $scope.selectedZone && $scope.selectedDivision && $scope.selectedCircle &&
                $scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone][$scope.selectedDivision][$scope.selectedCircle]) {
              $scope.wards = $scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone][$scope.selectedDivision][$scope.selectedCircle];
            }
          };

          function populateHierarchy(corp, zone, div, circle, ward) {
            $scope.selectedCorp = corp || '';
            if ($scope.selectedCorp && $scope.hierarchyData[$scope.selectedCorp]) {
              $scope.zones = Object.keys($scope.hierarchyData[$scope.selectedCorp]);
              $scope.selectedZone = zone || '';
              if ($scope.selectedZone && $scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone]) {
                $scope.divisions = Object.keys($scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone]);
                $scope.selectedDivision = div || '';
                if ($scope.selectedDivision && $scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone][$scope.selectedDivision]) {
                  $scope.circles = Object.keys($scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone][$scope.selectedDivision]);
                  $scope.selectedCircle = circle || '';
                  if ($scope.selectedCircle && $scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone][$scope.selectedDivision][$scope.selectedCircle]) {
                    $scope.wards = $scope.hierarchyData[$scope.selectedCorp][$scope.selectedZone][$scope.selectedDivision][$scope.selectedCircle];
                    $scope.selectedWard = ward || '';
                  }
                }
              }
            } else {
              $scope.selectedZone = ''; $scope.selectedDivision = ''; $scope.selectedCircle = ''; $scope.selectedWard = '';
              $scope.zones = []; $scope.divisions = []; $scope.circles = []; $scope.wards = [];
            }
          }

          /* ── Rows ── */
          function newRow() {
            return { isMaterial: 'Yes', description: '', num: 1, length: null, breadth: null, depth: null,
                     quantity: 1, rate: 0, unit: '', amount: 0, suggestions: [], showSuggestions: false, searching: false, itemId: null };
          }
          $scope.rows = [newRow()];

          /* ── Autocomplete ── */
          $scope.activeRow = null;

          function scrollActiveIntoView() {
            $timeout(function () {
              var el = document.querySelector('.suggestions-list li.active');
              if (el) el.scrollIntoView({ block: 'nearest' });
            }, 10);
          }

          $scope.onDescriptionFocus = function ($event, row) {
            $scope.activeRow = row;
            $scope.positionDropdown($event.target);
            if (row.description && row.description.length >= MIN_SEARCH_LENGTH && row.suggestions && row.suggestions.length > 0) {
              row.showSuggestions = true;
            } else if (row.description && row.description.length >= MIN_SEARCH_LENGTH) {
              $scope.onDescriptionKeyup($event, row);
            }
          };

          $scope.onDescriptionKeydown = function ($event, row) {
            var s = row.suggestions || [];
            if ($event.keyCode === 40) {
              $event.preventDefault();
              if (s.length > 0) { row.selectedIndex = (row.selectedIndex === undefined || row.selectedIndex === null) ? 0 : Math.min(row.selectedIndex + 1, s.length - 1); scrollActiveIntoView(); }
            } else if ($event.keyCode === 38) {
              $event.preventDefault();
              if (s.length > 0) { row.selectedIndex = (row.selectedIndex === undefined || row.selectedIndex === null) ? s.length - 1 : Math.max(row.selectedIndex - 1, 0); scrollActiveIntoView(); }
            } else if ($event.keyCode === 13) {
              $event.preventDefault();
              if (row.showSuggestions && s.length > 0 && row.selectedIndex >= 0) { $scope.selectItem(row, s[row.selectedIndex]); }
            } else if ($event.keyCode === 27) {
              $event.preventDefault();
              row.showSuggestions = false;
              $scope.activeRow = null;
            }
          };

          $scope.onDescriptionKeyup = function ($event, row) {
            if ([38, 40, 13, 27].indexOf($event.keyCode) !== -1) return;
            var query = row.description;
            if (!query || query.length < MIN_SEARCH_LENGTH) {
              row.suggestions = []; row.showSuggestions = false;
              if ($scope.activeRow === row) $scope.activeRow = null;
              return;
            }
            $scope.activeRow = row;
            $scope.positionDropdown($event.target);
            var key = row.$$hashKey;
            if (searchTimers[key]) $timeout.cancel(searchTimers[key]);
            searchTimers[key] = $timeout(function () {
              row.searching = true; row.showSuggestions = true;
              $http.get(API_BASE + '/items/search', { params: { q: query } }).then(function (r) {
                row.suggestions = r.data; row.searching = false;
                row.showSuggestions = r.data.length > 0; row.selectedIndex = 0;
                $scope.apiError = null;
              }, function () {
                row.searching = false; row.suggestions = []; row.showSuggestions = false;
                $scope.apiError = 'Could not reach the server. Is the application running?';
              });
            }, SEARCH_DEBOUNCE_MS);
          };

          $scope.selectItem = function (row, item) {
            row.description = item.itemDescription; row.rate = item.rate || 0;
            row.unit = item.unit || ''; row.itemId = item.slno;
            row.suggestions = []; row.showSuggestions = false; row.selectedIndex = 0;
            if ($scope.activeRow === row) $scope.activeRow = null;
            computeAmount(row);
          };

          $scope.dropdownTop = '0px';
          $scope.dropdownLeft = '0px';

          $scope.positionDropdown = function (target) {
            if (!target) return;
            var rect = target.getBoundingClientRect();
            $scope.dropdownTop = (rect.bottom + window.scrollY + 4) + 'px';
            $scope.dropdownLeft = (rect.left + window.scrollX) + 'px';
          };

          $scope.closeAllSuggestions = function () {
            $scope.rows.forEach(function (r) { r.showSuggestions = false; });
            $scope.activeRow = null;
          };

          /* ── Calculations ── */
          function computeAmount(row) { row.amount = (row.quantity || 0) * (row.rate || 0); }

          $scope.computeRow = function (row) {
            var l = row.length, b = row.breadth, d = row.depth, num = row.num || 1;
            if (l === null && b === null && d === null) { row.quantity = num; }
            else { row.quantity = num * (l || 1) * (b || 1) * (d || 1); }
            computeAmount(row);
          };

          $scope.rowTotal = function () { return $scope.rows.reduce(function (s, r) { return s + (r.amount || 0); }, 0); };
          $scope.gstAmount = function () { return $scope.rowTotal() * (parseFloat($scope.gstPercent) || 0) / 100; };
          $scope.grandTotal = function () { return $scope.rowTotal() + $scope.gstAmount(); };
          $scope.computeTotals = function () {};

          $scope.addRow = function () { $scope.rows.push(newRow()); };
          $scope.removeRow = function (index) { if ($scope.rows.length > 1) $scope.rows.splice(index, 1); };

          /* ── Save / Load ── */
          $scope.savedEstimates = [];
          $scope.savingEstimate = false;
          $scope.currentEstimateId = null;

          $scope.loadSavedEstimates = function () {
            var params = { officerPhone: $scope.currentUser.phoneNumber };
            if ($scope.filterWard) params.wardName = $scope.filterWard;
            if ($scope.filterCircle) params.circleName = $scope.filterCircle;
            if ($scope.filterDivision) params.division = $scope.filterDivision;
            if ($scope.filterStatus) params.status = $scope.filterStatus;
            $http.get(API_BASE + '/estimates', { params: params })
              .then(function (r) { $scope.savedEstimates = r.data; buildSummary(); },
                    function () { $scope.apiError = 'Could not load saved estimates.'; });
          };

          $scope.filterWard = '';
          $scope.filterCircle = '';
          $scope.filterDivision = '';
          $scope.filterZone = '';
          $scope.filterStatus = '';
          $scope.summaryData = [];
          $scope.summaryGroupBy = '';
          $scope.selectedSummaryKey = '';
          $scope.drilldownEstimates = [];

          function buildSummary() {
            var role = $scope.currentUser.role;
            if (role === 'MANAGER' || role === 'DGM') {
              $scope.summaryData = [];
              return;
            }
            var groupField = role === 'GM' ? 'circleName' : role === 'CGM' ? 'division' : 'zoneName';
            $scope.summaryGroupBy = groupField === 'circleName' ? 'Circle' : groupField === 'division' ? 'Division' : 'Zone';
            var groups = {};
            $scope.savedEstimates.forEach(function (est) {
              var key = est[groupField] || 'Unknown';
              if (!groups[key]) groups[key] = { total: 0, draft: 0, pending: 0, approved: 0, grandTotal: 0 };
              groups[key].total++;
              var s = est.status || 'DRAFT';
              if (s === 'DRAFT') groups[key].draft++;
              else if (s === 'APPROVED') groups[key].approved++;
              else groups[key].pending++;
              groups[key].grandTotal += est.grandTotal || 0;
            });
            var arr = [];
            Object.keys(groups).forEach(function (k) {
              arr.push({ key: k, total: groups[k].total, draft: groups[k].draft, pending: groups[k].pending, approved: groups[k].approved, grandTotal: groups[k].grandTotal });
            });
            $scope.summaryData = arr;
            $scope.selectedSummaryKey = '';
            $scope.drilldownEstimates = [];
          }

          $scope.selectSummaryRow = function (key) {
            $scope.selectedSummaryKey = key;
            var role = $scope.currentUser.role;
            var field = role === 'GM' ? 'circleName' : role === 'CGM' ? 'division' : 'zoneName';
            $scope.drilldownEstimates = $scope.savedEstimates.filter(function (est) {
              return (est[field] || 'Unknown') === key;
            });
          };

          $scope.clearSummaryDrilldown = function () {
            $scope.selectedSummaryKey = '';
            $scope.drilldownEstimates = [];
          };

          $scope.saveEstimate = function () {
            if (!$scope.nameOfWork || $scope.nameOfWork.trim() === '') {
              ModalService.alert('Required', 'Please enter a Name of Work before saving.');
              return;
            }
            var itemsMapped = $scope.rows.map(function (row, idx) {
              return { sno: idx + 1, isMaterial: row.isMaterial, description: row.description,
                       num: row.num || 1, length: row.length, breadth: row.breadth, depth: row.depth,
                       quantity: row.quantity || 0, rate: row.rate || 0, unit: row.unit || '', amount: row.amount || 0 };
            });
            var estimateData = {
              id: $scope.currentEstimateId, status: $scope.currentEstimateStatus || 'DRAFT',
              nameOfWork: $scope.nameOfWork, gstPercent: parseFloat($scope.gstPercent) || 0,
              items: itemsMapped, corp: $scope.selectedCorp, zoneName: $scope.selectedZone,
              division: $scope.selectedDivision, circleName: $scope.selectedCircle, wardName: $scope.selectedWard,
              officerPhone: $scope.currentUser.phoneNumber,
              preparedByName: $scope.currentPreparedByName, preparedByDesignation: $scope.currentPreparedByDesignation,
              verifiedByName: $scope.currentVerifiedByName, verifiedByDesignation: $scope.currentVerifiedByDesignation,
              recommendedByName: $scope.currentRecommendedByName, recommendedByDesignation: $scope.currentRecommendedByDesignation,
              forwardedByName: $scope.currentForwardedByName, forwardedByDesignation: $scope.currentForwardedByDesignation,
              sanctionedByName: $scope.currentSanctionedByName, sanctionedByDesignation: $scope.currentSanctionedByDesignation
            };
            localStorage.setItem(APP_CONFIG.ESTIMATE_KEY, JSON.stringify(estimateData));
            window.location.href = 'abstract.html';
          };

          $scope.loadEstimate = function (id) {
            $scope.apiError = null;
            $http.get(API_BASE + '/estimates/' + id).then(function (r) {
              var est = r.data;
              $scope.currentEstimateId = est.id;
              $scope.currentEstimateStatus = est.status || 'DRAFT';
              $scope.currentPreparedByName = est.preparedByName;
              $scope.currentPreparedByDesignation = est.preparedByDesignation;
              $scope.currentVerifiedByName = est.verifiedByName;
              $scope.currentVerifiedByDesignation = est.verifiedByDesignation;
              $scope.currentRecommendedByName = est.recommendedByName;
              $scope.currentRecommendedByDesignation = est.recommendedByDesignation;
              $scope.currentForwardedByName = est.forwardedByName;
              $scope.currentForwardedByDesignation = est.forwardedByDesignation;
              $scope.currentSanctionedByName = est.sanctionedByName;
              $scope.currentSanctionedByDesignation = est.sanctionedByDesignation;
              $scope.nameOfWork = est.nameOfWork;
              $scope.gstPercent = String(est.gstPercent);
              populateHierarchy(est.corp, est.zoneName, est.division, est.circleName, est.wardName);
              $scope.rows = est.items.map(function (item) {
                return { isMaterial: item.isMaterial || 'Yes', description: item.description || '',
                         num: item.num || 1, length: item.length || 0, breadth: item.breadth || 0,
                         depth: item.depth || 0, quantity: item.quantity || 0, rate: item.rate || 0,
                         unit: item.unit || '', amount: item.amount || 0, suggestions: [], showSuggestions: false, searching: false, itemId: item.id };
              });
              if ($scope.rows.length === 0) $scope.rows = [newRow()];
            }, function () { $scope.apiError = 'Could not load the estimate.'; });
          };

          $scope.deleteEstimate = function (id) {
            ModalService.confirm('Delete Estimate', 'Are you sure you want to delete estimate #' + id + '?', function () {
              $scope.apiError = null;
              $http.delete(API_BASE + '/estimates/' + id, { params: { officerPhone: $scope.currentUser.phoneNumber } })
                .then(function () {
                  if ($scope.currentEstimateId === id) $scope.resetForm();
                  $scope.loadSavedEstimates();
                }, function () { $scope.apiError = 'Could not delete the estimate.'; });
            });
          };

          $scope.resetForm = function () {
            $scope.currentEstimateId = null;
            $scope.currentEstimateStatus = 'DRAFT';
            $scope.currentPreparedByName = null; $scope.currentPreparedByDesignation = null;
            $scope.currentVerifiedByName = null; $scope.currentVerifiedByDesignation = null;
            $scope.currentRecommendedByName = null; $scope.currentRecommendedByDesignation = null;
            $scope.currentForwardedByName = null; $scope.currentForwardedByDesignation = null;
            $scope.currentSanctionedByName = null; $scope.currentSanctionedByDesignation = null;
            $scope.nameOfWork = ''; $scope.gstPercent = '0';
            $scope.rows = [newRow()];
            populateHierarchy('', '', '', '', '');
            localStorage.removeItem(APP_CONFIG.ESTIMATE_KEY);
          };

          /* ── Init ── */
          buildLocationOptions();

          var savedState = localStorage.getItem(APP_CONFIG.ESTIMATE_KEY);
          if (savedState) {
            try {
              var est = JSON.parse(savedState);
              $scope.currentEstimateId = est.id;
              $scope.currentEstimateStatus = est.status || 'DRAFT';
              $scope.currentPreparedByName = est.preparedByName;
              $scope.currentPreparedByDesignation = est.preparedByDesignation;
              $scope.currentVerifiedByName = est.verifiedByName;
              $scope.currentVerifiedByDesignation = est.verifiedByDesignation;
              $scope.currentRecommendedByName = est.recommendedByName;
              $scope.currentRecommendedByDesignation = est.recommendedByDesignation;
              $scope.currentForwardedByName = est.forwardedByName;
              $scope.currentForwardedByDesignation = est.forwardedByDesignation;
              $scope.currentSanctionedByName = est.sanctionedByName;
              $scope.currentSanctionedByDesignation = est.sanctionedByDesignation;
              $scope.nameOfWork = est.nameOfWork;
              $scope.gstPercent = String(est.gstPercent);
              populateHierarchy(est.corp, est.zoneName, est.division, est.circleName, est.wardName);
              if (est.items && est.items.length > 0) {
                $scope.rows = est.items.map(function (item) {
                  return { isMaterial: item.isMaterial || 'Yes', description: item.description || '',
                           num: item.num || 1, length: item.length || 0, breadth: item.breadth || 0,
                           depth: item.depth || 0, quantity: item.quantity || 0, rate: item.rate || 0,
                           unit: item.unit || '', amount: item.amount || 0, suggestions: [], showSuggestions: false, searching: false, itemId: item.id };
                });
              }
            } catch (e) { console.error('Error parsing saved state', e); }
          }
        }
      ]);
  </script>

  <!-- Global Suggestions Dropdown -->
  <div class="global-suggestions" ng-show="activeRow && activeRow.showSuggestions"
       ng-style="{'top': dropdownTop, 'left': dropdownLeft, 'width': '650px', 'display': activeRow && activeRow.showSuggestions ? 'block' : 'none'}"
       ng-click="$event.stopPropagation()">
    <div class="search-loading" ng-if="activeRow.searching">
      <span class="spinner spinner-dark"></span> Searching items...
    </div>
    <div class="no-results" ng-if="!activeRow.searching && activeRow.suggestions.length === 0">No results found</div>
    <ul class="suggestions-list" ng-if="!activeRow.searching && activeRow.suggestions.length > 0">
      <li ng-repeat="item in activeRow.suggestions" ng-class="{active: activeRow.selectedIndex === $index}"
          ng-mousedown="selectItem(activeRow, item)">{{item.itemDescription}}</li>
    </ul>
  </div>

</body>
</html>
```

#### File: `src/main/resources/static/abstract.html`
```html
<!DOCTYPE html>
<html lang="en" ng-app="hmwssbAbstractApp">

<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>HMWSSB – General Abstract</title>
  <link rel="stylesheet" href="styles/theme.css" />
  <script src="https://cdnjs.cloudflare.com/ajax/libs/angular.js/1.8.3/angular.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/xlsx-js-style@1.2.0/dist/xlsx.min.js"></script>
  <script src="config.js"></script>
  <script src="app.js"></script>
  <script src="hierarchy.js"></script>
  <style>
    .abstract-container {
      max-width: 900px;
      margin: 0 auto;
    }

    .doc-header {
      background: var(--primary);
      color: #fff;
      padding: 14px 20px;
      border-radius: var(--radius-md) var(--radius-md) 0 0;
      text-align: center;
    }

    .doc-header-title {
      font-size: 16px;
      font-weight: 700;
      letter-spacing: 0.5px;
      text-transform: uppercase;
    }

    .doc-header-sub {
      font-size: 11px;
      opacity: 0.85;
      margin-top: 3px;
    }

    /* ── User Bar ── */
    .user-bar {
      background: var(--bg-highlight);
      border: 1px solid var(--border-light);
      border-top: none;
      padding: 8px 16px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .user-avatar {
      width: 32px;
      height: 32px;
      background: var(--primary);
      color: #fff;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      font-weight: 700;
      flex-shrink: 0;
    }

    .user-name {
      font-weight: 700;
      font-size: 13px;
      color: #2c3e50;
    }

    .user-designation {
      font-size: 11px;
      color: var(--text-light);
    }

    .name-of-work-box {
      background: var(--bg-highlight);
      border: 1px solid var(--border-light);
      border-top: none;
      padding: 12px 16px;
    }

    .now-label {
      font-weight: 700;
      color: var(--error-text);
    }

    .now-label::before {
      content: '* ';
    }

    .now-value {
      font-weight: 600;
    }

    .location-grid {
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      gap: 8px;
      margin-top: 8px;
      padding-top: 8px;
      border-top: 1px dashed var(--border-light);
      font-size: 11px;
    }

    @media (max-width: 768px) {
      .location-grid { grid-template-columns: repeat(2, 1fr); }
      .sig-grid { grid-template-columns: repeat(2, 1fr) !important; }
    }

    .location-grid span {
      font-weight: 700;
      color: var(--error-text);
    }

    /* ── Abstract Table ── */
    .abstract-table {
      width: 100%;
      border-collapse: collapse;
      margin-bottom: 16px;
    }

    .abstract-table th, .abstract-table td {
      border: 1px solid var(--table-border);
      padding: 8px 10px;
      text-align: center;
      vertical-align: middle;
    }

    .abstract-table th {
      background: var(--table-header-bg);
      color: var(--table-header-text);
      font-weight: 600;
      font-size: 12px;
      text-transform: uppercase;
    }

    .abstract-table tbody td {
      background: var(--table-row-odd);
    }

    .abstract-table tbody tr:nth-child(even) td {
      background: var(--table-row-even);
    }

    .part-header td {
      font-weight: 700;
      background: var(--table-header-bg) !important;
      color: var(--table-header-text);
      text-align: left;
    }

    .part-total td {
      background: var(--status-draft-bg) !important;
      color: var(--status-draft-text);
      font-weight: 700;
    }

    .grand-total td {
      background: var(--primary) !important;
      color: #fff !important;
      font-weight: 700;
    }

    .ls-input {
      width: 100%;
      border: 1px solid #bbb;
      border-radius: var(--radius-sm);
      padding: 4px 6px;
      font-size: 12px;
      text-align: right;
      font-weight: 700;
      background: #fff;
    }

    .ls-input:focus {
      border-color: var(--border-input-focus);
      outline: none;
    }

    /* ── Total in Words ── */
    .words-box {
      background: var(--bg-highlight);
      border: 1px solid var(--border-light);
      border-radius: var(--radius-sm);
      padding: 10px 14px;
      font-weight: 700;
      text-align: center;
      font-size: 13px;
      margin: 15px 0 20px;
      color: var(--table-header-text);
    }

    /* ── Signature Block ── */
    .sig-grid {
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      gap: 12px;
      margin: 25px 0;
      padding-top: 15px;
      border-top: 1px solid var(--border-light);
    }

    .sig-card {
      padding: 10px;
      border: 1px dashed rgba(122, 122, 56, 0.3);
      border-radius: var(--radius-sm);
      background: rgba(255, 255, 255, 0.4);
      text-align: center;
    }

    .sig-role {
      font-size: 9px;
      color: var(--text-light);
      font-weight: 700;
      text-transform: uppercase;
      margin-bottom: 5px;
    }

    .sig-name {
      font-weight: 700;
      color: #2c3e50;
      font-size: 12px;
    }

    .sig-designation {
      font-size: 10px;
      color: var(--text-muted);
      margin-top: 2px;
    }

    .sig-status {
      font-size: 9px;
      font-weight: 600;
      margin-top: 3px;
    }

    .sig-signed {
      color: var(--btn-success);
    }

    .sig-pending {
      color: var(--text-light);
      font-style: italic;
    }

    /* ── Action Bar ── */
    .action-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-top: 1px dashed var(--border-light);
      padding-top: 16px;
      margin-top: 16px;
      flex-wrap: wrap;
      gap: 8px;
    }

    .action-group {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }

    /* ── Print ── */
    @media print {
      body { background: white; padding: 0; color: #000; }
      .abstract-container { border: none; box-shadow: none; max-width: 100%; }
      .doc-header { background: transparent; color: #000; border-bottom: 2px solid #000; }
      .doc-header-title { font-size: 18px; }
      .name-of-work-box { background: transparent; border: none; }
      .words-box { background: transparent; border: none; color: #000; }
      .abstract-table th, .abstract-table td { border: 1px solid #000; background: transparent !important; color: #000 !important; }
      .part-header td, .grand-total td { background: transparent !important; color: #000 !important; border-bottom: 2px solid #000; }
      .ls-input { border: none; background: transparent; color: #000; padding: 0; }
      .action-bar { display: none; }
      .sig-card { border: 1px solid #000; }
    }
  </style>
</head>

<body ng-controller="AbstractCtrl">

  <div class="abstract-container">

    <!-- Error Banner -->
    <div class="alert alert-error" ng-if="apiError">{{apiError}}</div>

    <!-- Document Header -->
    <div class="doc-header">
      <div class="doc-header-title">General Abstract</div>
      <div class="doc-header-sub">Hyderabad Metropolitan Water Supply and Sewerage Board</div>
    </div>

    <!-- User Bar -->
    <div class="user-bar" style="border-radius:0;border-top:none;">
      <div class="user-info">
        <div class="user-avatar">{{currentUser.name.charAt(0)}}</div>
        <div>
          <div class="user-name">{{currentUser.name}}</div>
          <div class="user-designation">{{currentUser.designation}} &bull; {{currentUser.phoneNumber}}</div>
        </div>
      </div>
      <div style="display:flex;align-items:center;gap:8px;">
        <span class="badge" ng-class="getStatusBadgeClass(status)">{{getStatusLabel(status)}}</span>
        <button class="btn btn-danger btn-sm" ng-click="logout()">Logout</button>
      </div>
    </div>

    <!-- Name of Work Box -->
    <div class="name-of-work-box">
      <div><span class="now-label">Name of Work:</span> <span class="now-value">{{nameOfWork}}</span></div>
      <div class="location-grid">
        <div><span>CORP:</span> {{corp || 'N/A'}}</div>
        <div><span>Zone:</span> {{zoneName || 'N/A'}}</div>
        <div><span>Division:</span> {{division || 'N/A'}}</div>
        <div><span>Circle:</span> {{circleName || 'N/A'}}</div>
        <div><span>Ward:</span> {{wardName || 'N/A'}}</div>
      </div>
    </div>

    <!-- Abstract Table -->
    <table class="abstract-table">
      <thead>
        <tr>
          <th style="width:8%">Sl.No</th>
          <th style="width:58%">Description</th>
          <th style="width:6%"></th>
          <th style="width:28%">Amount Rs.</th>
        </tr>
      </thead>
      <tbody>
        <!-- Part I -->
        <tr class="part-header"><td colspan="4">Part-I - Working Items</td></tr>
        <tr>
          <td>1</td>
          <td class="text-left">Cost of Material</td>
          <td></td>
          <td class="text-right">{{costOfMaterial | number:2}}</td>
        </tr>
        <tr>
          <td>2</td>
          <td class="text-left">Cost of Civil work</td>
          <td></td>
          <td class="text-right">{{costOfCivilWork | number:2}}</td>
        </tr>
        <tr class="part-total">
          <td></td>
          <td class="text-right font-bold">Cost of Estimate : Part-I</td>
          <td></td>
          <td class="text-right">{{partITotal() | number:2}}</td>
        </tr>

        <!-- Part II -->
        <tr class="part-header"><td colspan="4">Part-II - Reimbursible Items</td></tr>
        <tr>
          <td>3</td>
          <td class="text-left">GST @ {{gstPercent}}%</td>
          <td>Rs.</td>
          <td class="text-right">{{gstAmount() | number:2}}</td>
        </tr>

        <!-- Part III -->
        <tr class="part-header"><td colspan="4">Part-III: LS provisions</td></tr>
        <tr>
          <td>4</td>
          <td class="text-left">LS unforeseen items and rounding off</td>
          <td>Rs.</td>
          <td class="text-right" style="padding:3px 5px;">
            <input type="number" class="ls-input" ng-model="unforeseenAmount" ng-change="updateTotals()" min="0" step="0.01" />
          </td>
        </tr>

        <!-- Grand Total -->
        <tr class="grand-total">
          <td>5</td>
          <td class="text-right">GRAND TOTAL (Part I + II + III)</td>
          <td>Rs.</td>
          <td class="text-right">{{grandTotal() | number:2}}</td>
        </tr>
      </tbody>
    </table>

    <!-- Total in Words -->
    <div class="words-box">{{totalInWords}}</div>

    <!-- Signature Block -->
    <div class="sig-grid">
      <div class="sig-card" ng-if="currentUser.role === 'MANAGER' || currentUser.role === 'DGM' || currentUser.role === 'GM' || currentUser.role === 'CGM' || currentUser.role === 'DOP'">
        <div class="sig-role">Prepared by (AE)</div>
        <div ng-if="preparedByName">
          <div class="sig-name">{{preparedByName}}</div>
          <div class="sig-designation">{{preparedByDesignation}}</div>
          <div class="sig-status sig-signed">&#10003; Signed</div>
        </div>
        <div ng-if="!preparedByName" class="sig-status sig-pending">Pending Signature</div>
      </div>
      <div class="sig-card" ng-if="currentUser.role === 'DGM' || currentUser.role === 'GM' || currentUser.role === 'CGM' || currentUser.role === 'DOP'">
        <div class="sig-role">Verified by (DGM)</div>
        <div ng-if="verifiedByName">
          <div class="sig-name">{{verifiedByName}}</div>
          <div class="sig-designation">{{verifiedByDesignation}}</div>
          <div class="sig-status sig-signed">&#10003; Signed</div>
        </div>
        <div ng-if="!verifiedByName" class="sig-status sig-pending">Pending Scrutiny</div>
      </div>
      <div class="sig-card" ng-if="currentUser.role === 'GM' || currentUser.role === 'CGM' || currentUser.role === 'DOP'">
        <div class="sig-role">Recommended by (GM)</div>
        <div ng-if="recommendedByName">
          <div class="sig-name">{{recommendedByName}}</div>
          <div class="sig-designation">{{recommendedByDesignation}}</div>
          <div class="sig-status sig-signed">&#10003; Signed</div>
        </div>
        <div ng-if="!recommendedByName" class="sig-status sig-pending">Pending Recom.</div>
      </div>
      <div class="sig-card" ng-if="currentUser.role === 'CGM' || currentUser.role === 'DOP'">
        <div class="sig-role">Reviewed by (CGM)</div>
        <div ng-if="forwardedByName">
          <div class="sig-name">{{forwardedByName}}</div>
          <div class="sig-designation">{{forwardedByDesignation}}</div>
          <div class="sig-status sig-signed">&#10003; Signed</div>
        </div>
        <div ng-if="!forwardedByName" class="sig-status sig-pending">Pending Review</div>
      </div>
      <div class="sig-card" ng-if="currentUser.role === 'DOP'">
        <div class="sig-role">Sanctioned by (DOP)</div>
        <div ng-if="sanctionedByName">
          <div class="sig-name">{{sanctionedByName}}</div>
          <div class="sig-designation">{{sanctionedByDesignation}}</div>
          <div class="sig-status sig-signed">&#10003; Approved</div>
        </div>
        <div ng-if="!sanctionedByName" class="sig-status sig-pending">Pending Sanction</div>
      </div>
    </div>

    <!-- Action Bar -->
    <div class="action-bar">
      <button class="btn btn-secondary" ng-click="goBack()">&#8592; Back</button>
      <div class="action-group">
        <button class="btn btn-success" ng-click="saveEstimateToDb()" ng-if="isEditable()" ng-disabled="saving">
          <span ng-if="saving"><span class="spinner"></span> Saving...</span>
          <span ng-if="!saving">Save Estimate</span>
        </button>
        <button class="btn btn-info" ng-click="performWorkflowAction('FORWARD')" ng-if="canForward()" ng-disabled="saving || workflowActing">
          <span ng-if="workflowActing"><span class="spinner"></span></span>
          <span ng-if="!workflowActing">&#10132; {{getForwardActionLabel()}}</span>
        </button>
        <button class="btn btn-warning" ng-click="performWorkflowAction('RETURN')" ng-if="canReturn()" ng-disabled="saving || workflowActing">
          <span ng-if="workflowActing"><span class="spinner"></span></span>
          <span ng-if="!workflowActing">&#8630; Return to {{getReturnActionLabel()}}</span>
        </button>
        <button class="btn btn-primary" onclick="window.print()" ng-disabled="saving || workflowActing">Print</button>
        <button class="btn" style="background:#1f7246;color:#fff;" ng-click="downloadExcel()" ng-disabled="saving || workflowActing">Download Excel</button>
      </div>
    </div>

  </div>

  <script>
    angular.module('hmwssbAbstractApp', ['hmwssbShared'])
      .controller('AbstractCtrl', ['$scope', '$http', 'AuthService', 'StatusService', 'ModalService', 'Utils',
        function ($scope, $http, AuthService, StatusService, ModalService, Utils) {

          if (!AuthService.requireLogin()) return;
          $scope.currentUser = AuthService.getUser();

          var API_BASE = APP_CONFIG.API_BASE;

          var savedState = localStorage.getItem(APP_CONFIG.ESTIMATE_KEY);
          if (!savedState) {
            ModalService.alert('No Data', 'No estimate data found. Returning to measurement sheet.', function () {
              window.location.href = 'index.html';
            });
            return;
          }

          var estimateData = JSON.parse(savedState);
          $scope.estimateId = estimateData.id;
          $scope.nameOfWork = estimateData.nameOfWork;
          $scope.costOfMaterial = estimateData.costOfMaterial || 0;
          $scope.costOfCivilWork = estimateData.costOfCivilWork || 0;
          $scope.gstPercent = estimateData.gstPercent || 0;
          $scope.unforeseenAmount = estimateData.unforeseenAmount || 0;
          $scope.items = estimateData.items || [];
          $scope.corp = estimateData.corp || '';
          $scope.zoneName = estimateData.zoneName || '';
          $scope.division = estimateData.division || '';
          $scope.circleName = estimateData.circleName || '';
          $scope.wardName = estimateData.wardName || '';
          $scope.status = estimateData.status || 'DRAFT';
          $scope.preparedByName = estimateData.preparedByName;
          $scope.preparedByDesignation = estimateData.preparedByDesignation;
          $scope.verifiedByName = estimateData.verifiedByName;
          $scope.verifiedByDesignation = estimateData.verifiedByDesignation;
          $scope.recommendedByName = estimateData.recommendedByName;
          $scope.recommendedByDesignation = estimateData.recommendedByDesignation;
          $scope.forwardedByName = estimateData.forwardedByName;
          $scope.forwardedByDesignation = estimateData.forwardedByDesignation;
          $scope.sanctionedByName = estimateData.sanctionedByName;
          $scope.sanctionedByDesignation = estimateData.sanctionedByDesignation;

          $scope.saving = false;
          $scope.workflowActing = false;
          $scope.apiError = null;
          $scope.totalInWords = '';

          // Compute cost of material and civil work from items
          $scope.costOfMaterial = 0;
          $scope.costOfCivilWork = 0;
          $scope.items.forEach(function (item) {
            if (Utils.isMaterialYes(item.isMaterial)) {
              $scope.costOfMaterial += item.amount || 0;
            } else {
              $scope.costOfCivilWork += item.amount || 0;
            }
          });

          $scope.getStatusLabel = StatusService.getLabel;
          $scope.getStatusBadgeClass = StatusService.getBadgeClass;
          $scope.isEditable = function () { return StatusService.isEditable($scope.status, $scope.currentUser.role); };
          $scope.canForward = function () { return StatusService.canForward($scope.status, $scope.currentUser.role); };
          $scope.canReturn = function () { return StatusService.canReturn($scope.status, $scope.currentUser.role); };
          $scope.getForwardActionLabel = function () { return StatusService.getForwardLabel($scope.status); };
          $scope.getReturnActionLabel = function () { return StatusService.getReturnLabel($scope.status); };
          $scope.logout = function () { AuthService.logout(); };

          $scope.partITotal = function () { return $scope.costOfMaterial + $scope.costOfCivilWork; };
          $scope.gstAmount = function () { return $scope.partITotal() * $scope.gstPercent / 100; };
          $scope.grandTotal = function () { return $scope.partITotal() + $scope.gstAmount() + ($scope.unforeseenAmount || 0); };

          $scope.updateTotals = function () {
            estimateData.unforeseenAmount = $scope.unforeseenAmount;
            localStorage.setItem(APP_CONFIG.ESTIMATE_KEY, JSON.stringify(estimateData));
            $scope.totalInWords = 'Rupees ' + Utils.numberToWords($scope.grandTotal());
          };

          $scope.updateTotals();

          $scope.goBack = function () { window.location.href = 'index.html'; };

          $scope.performWorkflowAction = function (actionType) {
            if (!$scope.estimateId) {
              ModalService.alert('Required', 'Please save the estimate first.');
              return;
            }
            var actionLabel = actionType === 'FORWARD' ? 'forward/approve' : 'return';
            ModalService.confirm('Confirm Action', 'Are you sure you want to ' + actionLabel + ' this estimate?', function () {
              $scope.workflowActing = true;
              $scope.apiError = null;
              $http.post(API_BASE + '/estimates/' + $scope.estimateId + '/action', {
                action: actionType, officerPhone: $scope.currentUser.phoneNumber
              }).then(function (response) {
                $scope.workflowActing = false;
                var updated = response.data;
                $scope.status = updated.status;
                $scope.preparedByName = updated.preparedByName;
                $scope.preparedByDesignation = updated.preparedByDesignation;
                $scope.verifiedByName = updated.verifiedByName;
                $scope.verifiedByDesignation = updated.verifiedByDesignation;
                $scope.recommendedByName = updated.recommendedByName;
                $scope.recommendedByDesignation = updated.recommendedByDesignation;
                $scope.forwardedByName = updated.forwardedByName;
                $scope.forwardedByDesignation = updated.forwardedByDesignation;
                $scope.sanctionedByName = updated.sanctionedByName;
                $scope.sanctionedByDesignation = updated.sanctionedByDesignation;
                estimateData.id = updated.id;
                estimateData.status = updated.status;
                estimateData.preparedByName = updated.preparedByName;
                estimateData.preparedByDesignation = updated.preparedByDesignation;
                estimateData.verifiedByName = updated.verifiedByName;
                estimateData.verifiedByDesignation = updated.verifiedByDesignation;
                estimateData.recommendedByName = updated.recommendedByName;
                estimateData.recommendedByDesignation = updated.recommendedByDesignation;
                estimateData.forwardedByName = updated.forwardedByName;
                estimateData.forwardedByDesignation = updated.forwardedByDesignation;
                estimateData.sanctionedByName = updated.sanctionedByName;
                estimateData.sanctionedByDesignation = updated.sanctionedByDesignation;
                localStorage.setItem(APP_CONFIG.ESTIMATE_KEY, JSON.stringify(estimateData));
                ModalService.alert('Success', 'Workflow action completed. New status: ' + StatusService.getLabel($scope.status));
              }, function (error) {
                $scope.workflowActing = false;
                $scope.apiError = 'Failed: ' + (error.data || 'unknown error');
              });
            });
          };

          $scope.saveEstimateToDb = function () {
            $scope.saving = true;
            $scope.apiError = null;
            var payload = {
              id: $scope.estimateId, nameOfWork: $scope.nameOfWork,
              gstPercent: parseFloat($scope.gstPercent) || 0,
              unforeseenAmount: parseFloat($scope.unforeseenAmount) || 0,
              grandTotal: $scope.grandTotal(), items: $scope.items,
              corp: $scope.corp, zoneName: $scope.zoneName, division: $scope.division,
              circleName: $scope.circleName, wardName: $scope.wardName,
              officerPhone: $scope.currentUser.phoneNumber
            };
            $http.post(API_BASE + '/estimates', payload).then(function (response) {
              var saved = response.data;
              $scope.estimateId = saved.id;
              estimateData.id = saved.id;
              localStorage.setItem(APP_CONFIG.ESTIMATE_KEY, JSON.stringify(estimateData));
              $scope.saving = false;
              ModalService.alert('Saved', 'Estimate saved successfully with ID: ' + saved.id);
            }, function () {
              $scope.saving = false;
              $scope.apiError = 'Failed to save estimate. Make sure the server is running.';
            });
          };

          $scope.downloadExcel = function () {
            function makeCell(val, isBold, align, showBorder, size, numFormat) {
              var cell = { v: val };
              if (typeof val === 'number') { cell.t = 'n'; cell.z = numFormat || '#,##,##0.00'; }
              else { cell.t = 's'; }
              cell.s = { font: { name: 'Arial', sz: size || 10, bold: !!isBold }, alignment: { horizontal: align || 'left', vertical: 'center', wrapText: true } };
              if (showBorder) { cell.s.border = { top: { style: 'thin', color: { rgb: '000000' } }, bottom: { style: 'thin', color: { rgb: '000000' } }, left: { style: 'thin', color: { rgb: '000000' } }, right: { style: 'thin', color: { rgb: '000000' } } }; }
              return cell;
            }

            var wb = XLSX.utils.book_new();

            // Sheet 1: Materials
            var matRows = [];
            matRows.push([makeCell('Estimate for Material', true, 'center', false, 12)].concat(Array(9).fill(null).map(function(){return makeCell('',false,'center',false);})));
            matRows.push([makeCell('Name of Work: ' + $scope.nameOfWork, true, 'center', false, 9)].concat(Array(9).fill(null).map(function(){return makeCell('',false,'center',false);})));
            matRows.push(Array(10).fill(null).map(function(){return makeCell('',false,'center',false);}));
            matRows.push(['S.No','Description of Work','No','L','B','D','Qty','Rate','Unit','Amount'].map(function(h){return makeCell(h,true,'center',true,9);}));

            var matIdx = 1, matTotal = 0;
            $scope.items.forEach(function (item) {
              if (Utils.isMaterialYes(item.isMaterial)) {
                matRows.push([makeCell(matIdx++,false,'center',true,null,'0'), makeCell(item.description,false,'left',true), makeCell(item.num||1,false,'right',true,null,'0'), makeCell(item.length>0?item.length:'',false,'right',true), makeCell(item.breadth>0?item.breadth:'',false,'right',true), makeCell(item.depth>0?item.depth:'',false,'right',true), makeCell(item.quantity||0,false,'right',true), makeCell(item.rate||0,false,'right',true), makeCell(item.unit||'',false,'center',true), makeCell(item.amount||0,false,'right',true)]);
                matTotal += item.amount || 0;
              }
            });
            var matSubIdx = matRows.length;
            matRows.push([makeCell('Part-I: Materials Total',true,'right',true,9)].concat(Array(8).fill(null).map(function(){return makeCell('',true,'right',true,9);})).concat([makeCell(matTotal,true,'right',true,9)]));

            var ws1 = XLSX.utils.aoa_to_sheet(matRows);
            ws1['!cols'] = [{wch:6},{wch:45},{wch:6},{wch:8},{wch:8},{wch:8},{wch:10},{wch:10},{wch:8},{wch:15}];
            ws1['!merges'] = [{s:{r:0,c:0},e:{r:0,c:9}},{s:{r:1,c:0},e:{r:1,c:9}},{s:{r:matSubIdx,c:0},e:{r:matSubIdx,c:8}}];

            // Sheet 2: Civil Works
            var civRows = [];
            civRows.push([makeCell('Estimate for Civil Work',true,'center',false,12)].concat(Array(9).fill(null).map(function(){return makeCell('',false,'center',false);})));
            civRows.push([makeCell('Name of Work: ' + $scope.nameOfWork,true,'center',false,9)].concat(Array(9).fill(null).map(function(){return makeCell('',false,'center',false);})));
            civRows.push(Array(10).fill(null).map(function(){return makeCell('',false,'center',false);}));
            civRows.push(['S.No','Description of Work','No','L','B','D','Qty','Rate','Unit','Amount'].map(function(h){return makeCell(h,true,'center',true,9);}));

            var civIdx = 1, civTotal = 0;
            $scope.items.forEach(function (item) {
              if (!Utils.isMaterialYes(item.isMaterial)) {
                civRows.push([makeCell(civIdx++,false,'center',true,null,'0'), makeCell(item.description,false,'left',true), makeCell(item.num||1,false,'right',true,null,'0'), makeCell(item.length>0?item.length:'',false,'right',true), makeCell(item.breadth>0?item.breadth:'',false,'right',true), makeCell(item.depth>0?item.depth:'',false,'right',true), makeCell(item.quantity||0,false,'right',true), makeCell(item.rate||0,false,'right',true), makeCell(item.unit||'',false,'center',true), makeCell(item.amount||0,false,'right',true)]);
                civTotal += item.amount || 0;
              }
            });
            var civSubIdx = civRows.length;
            civRows.push([makeCell('Part-I: Working Items Total',true,'right',true,9)].concat(Array(8).fill(null).map(function(){return makeCell('',true,'right',true,9);})).concat([makeCell(civTotal,true,'right',true,9)]));

            var ws2 = XLSX.utils.aoa_to_sheet(civRows);
            ws2['!cols'] = [{wch:6},{wch:45},{wch:6},{wch:8},{wch:8},{wch:8},{wch:10},{wch:10},{wch:8},{wch:15}];
            ws2['!merges'] = [{s:{r:0,c:0},e:{r:0,c:9}},{s:{r:1,c:0},e:{r:1,c:9}},{s:{r:civSubIdx,c:0},e:{r:civSubIdx,c:8}}];

            // Sheet 3: General Abstract
            var absRows = [];
            absRows.push([makeCell('GENERAL ABSTRACT',true,'center',false,14)].concat(Array(3).fill(null).map(function(){return makeCell('',false,'center',false);})));
            absRows.push([makeCell('Name of Work: ' + $scope.nameOfWork,true,'left',false,9)].concat(Array(3).fill(null).map(function(){return makeCell('',false,'center',false);})));
            var locStr = 'CORP: ' + ($scope.corp||'N/A') + ' | Zone: ' + ($scope.zoneName||'N/A') + ' | Div: ' + ($scope.division||'N/A') + ' | Circle: ' + ($scope.circleName||'N/A') + ' | Ward: ' + ($scope.wardName||'N/A');
            absRows.push([makeCell(locStr,true,'left',false,9)].concat(Array(3).fill(null).map(function(){return makeCell('',false,'center',false);})));
            absRows.push(Array(4).fill(null).map(function(){return makeCell('',false,'center',false);}));
            absRows.push([makeCell('Sl.No',true,'center',true,9),makeCell('Description',true,'left',true,9),makeCell('',true,'center',true,9),makeCell('Amount Rs.',true,'right',true,9)]);
            absRows.push([makeCell('Part-I - Working Items',true,'left',true,9)].concat(Array(3).fill(null).map(function(){return makeCell('',true,'left',true,9);})));
            absRows.push([makeCell(1,false,'center',true,null,'0'),makeCell('Cost of Material',false,'left',true),makeCell('',false,'center',true),makeCell($scope.costOfMaterial,false,'right',true)]);
            absRows.push([makeCell(2,false,'center',true,null,'0'),makeCell('Cost of Civil work',false,'left',true),makeCell('',false,'center',true),makeCell($scope.costOfCivilWork,false,'right',true)]);
            absRows.push([makeCell('Cost of Estimate : Part-I',true,'right',true,9)].concat(Array(2).fill(null).map(function(){return makeCell('',true,'right',true,9);})).concat([makeCell($scope.partITotal(),true,'right',true,9)]));
            absRows.push([makeCell('Part-II - Reimbursible Items',true,'left',true,9)].concat(Array(3).fill(null).map(function(){return makeCell('',true,'left',true,9);})));
            absRows.push([makeCell(3,false,'center',true,null,'0'),makeCell('GST @ ' + $scope.gstPercent + '%',false,'left',true),makeCell('Rs.',false,'center',true),makeCell($scope.gstAmount(),false,'right',true)]);
            absRows.push([makeCell('Part-III: LS provisions',true,'left',true,9)].concat(Array(3).fill(null).map(function(){return makeCell('',true,'left',true,9);})));
            absRows.push([makeCell(4,false,'center',true,null,'0'),makeCell('LS unforeseen items and rounding off',false,'left',true),makeCell('Rs.',false,'center',true),makeCell($scope.unforeseenAmount||0,false,'right',true)]);
            absRows.push([makeCell(5,true,'center',true,9,null,'0'),makeCell('Grand Total (Part I + II + III)',true,'right',true,9),makeCell('Rs.',true,'center',true,9),makeCell($scope.grandTotal(),true,'right',true,9)]);
            absRows.push(Array(4).fill(null).map(function(){return makeCell('',false,'center',false);}));
            absRows.push([makeCell($scope.totalInWords,true,'center',false,9)].concat(Array(3).fill(null).map(function(){return makeCell('',true,'center',false);})));
            absRows.push(Array(4).fill(null).map(function(){return makeCell('',false,'center',false);}));
            absRows.push([makeCell('Prepared by (AE):',true,'left',false),makeCell($scope.preparedByName||'Pending',false,'left',false),makeCell('Designation:',true,'right',false),makeCell($scope.preparedByDesignation||'N/A',false,'left',false)]);
            absRows.push([makeCell('Verified by (DGM):',true,'left',false),makeCell($scope.verifiedByName||'Pending',false,'left',false),makeCell('Designation:',true,'right',false),makeCell($scope.verifiedByDesignation||'N/A',false,'left',false)]);
            absRows.push([makeCell('Recommended by (GM):',true,'left',false),makeCell($scope.recommendedByName||'Pending',false,'left',false),makeCell('Designation:',true,'right',false),makeCell($scope.recommendedByDesignation||'N/A',false,'left',false)]);
            absRows.push([makeCell('Reviewed by (CGM):',true,'left',false),makeCell($scope.forwardedByName||'Pending',false,'left',false),makeCell('Designation:',true,'right',false),makeCell($scope.forwardedByDesignation||'N/A',false,'left',false)]);
            absRows.push([makeCell('Sanctioned by (DOP):',true,'left',false),makeCell($scope.sanctionedByName||'Pending',false,'left',false),makeCell('Designation:',true,'right',false),makeCell($scope.sanctionedByDesignation||'N/A',false,'left',false)]);

            var ws3 = XLSX.utils.aoa_to_sheet(absRows);
            ws3['!cols'] = [{wch:18},{wch:35},{wch:15},{wch:25}];
            ws3['!merges'] = [{s:{r:0,c:0},e:{r:0,c:3}},{s:{r:1,c:0},e:{r:1,c:3}},{s:{r:2,c:0},e:{r:2,c:3}},{s:{r:5,c:0},e:{r:5,c:3}},{s:{r:9,c:0},e:{r:9,c:3}},{s:{r:11,c:0},e:{r:11,c:3}},{s:{r:15,c:0},e:{r:15,c:3}}];

            XLSX.utils.book_append_sheet(wb, ws1, 'Materials');
            XLSX.utils.book_append_sheet(wb, ws2, 'Civil Works');
            XLSX.utils.book_append_sheet(wb, ws3, 'General Abstract');

            var cleanName = ($scope.nameOfWork || 'Estimate').substring(0, 30).replace(/[^a-zA-Z0-9]/g, '_');
            XLSX.writeFile(wb, 'Estimate_Abstract_' + cleanName + '.xlsx');
          };
        }
      ]);
  </script>

</body>
</html>
```

---

### 6.8 PowerShell Helper Scripts

#### File: `clean_and_copy_json.ps1`
```powershell
$jsonPath = "e:\works\users_parsed.json"
$destDir = "e:\works\src\main\resources"
$destPath = Join-Path $destDir "users_parsed.json"

if (-not (Test-Path $destDir)) {
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
}

Copy-Item $jsonPath $destPath -Force
Write-Output "Copied users JSON to $destPath"
```

#### File: `read_xls.ps1`
```powershell
$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$excel.DisplayAlerts = $false
$filePath = "e:\works\ESCALATION_NEW_ZONE_CIRCLE.xls"
$workbook = $excel.Workbooks.Open($filePath)

foreach ($sheet in $workbook.Sheets) {
    Write-Output "=== SHEET: $($sheet.Name) ==="
    $usedRange = $sheet.UsedRange
    $rowCount = $usedRange.Rows.Count
    $colCount = $usedRange.Columns.Count
    
    for ($r = 1; $r -le $rowCount; $r++) {
        $rowVals = @()
        for ($c = 1; $c -le $colCount; $c++) {
            $val = $usedRange.Cells.Item($r, $c).Text
            $rowVals += $val
        }
        Write-Output ($rowVals -join "`t")
    }
}

$workbook.Close($false)
$excel.Quit()
[System.Runtime.Interopservices.Marshal]::ReleaseComObject($excel) | Out-Null
```

#### File: `read_xls_oledb.ps1`
```powershell
try {
    $connStr = "Provider=Microsoft.ACE.OLEDB.12.0;Data Source=E:\works\ESCALATION_NEW_ZONE_CIRCLE.xls;Extended Properties='Excel 8.0;HDR=Yes;IMEX=1';"
    $conn = New-Object System.Data.OleDb.OleDbConnection($connStr)
    $conn.Open()
    Write-Output "OLEDB 12.0 Connection Successful!"
    $cmd = New-Object System.Data.OleDb.OleDbCommand("SELECT * FROM [Sheet1$]", $conn)
    $da = New-Object System.Data.OleDb.OleDbDataAdapter($cmd)
    $dt = New-Object System.Data.DataTable
    $da.Fill($dt) | Out-Null
    foreach ($row in $dt.Rows) {
        Write-Output "$($row[0])`t$($row[1])`t$($row[2])`t$($row[3])"
    }
    $conn.Close()
} catch {
    Write-Output "OLEDB 12.0 failed: $_"
    try {
        $connStr = "Provider=Microsoft.Jet.OLEDB.4.0;Data Source=E:\works\ESCALATION_NEW_ZONE_CIRCLE.xls;Extended Properties='Excel 8.0;HDR=Yes;IMEX=1';"
        $conn = New-Object System.Data.OleDb.OleDbConnection($connStr)
        $conn.Open()
        Write-Output "OLEDB 4.0 Connection Successful!"
        $cmd = New-Object System.Data.OleDb.OleDbCommand("SELECT * FROM [Sheet1$]", $conn)
        $da = New-Object System.Data.OleDb.OleDbDataAdapter($cmd)
        $dt = New-Object System.Data.DataTable
        $da.Fill($dt) | Out-Null
        foreach ($row in $dt.Rows) {
            Write-Output "$($row[0])`t$($row[1])`t$($row[2])`t$($row[3])"
        }
        $conn.Close()
    } catch {
        Write-Output "OLEDB 4.0 failed: $_"
    }
}
```

#### File: `read_schema_oledb.ps1`
```powershell
try {
    $connStr = "Provider=Microsoft.ACE.OLEDB.12.0;Data Source=E:\works\ESCALATION_NEW_ZONE_CIRCLE.xls;Extended Properties='Excel 8.0;HDR=No;IMEX=1';"
    $conn = New-Object System.Data.OleDb.OleDbConnection($connStr)
    $conn.Open()
    Write-Output "--- Sheets (Tables) ---"
    $schemaTable = $conn.GetSchema("Tables")
    foreach ($row in $schemaTable.Rows) {
        Write-Output "Table Name: $($row['TABLE_NAME'])"
    }
    Write-Output ""

    foreach ($tblRow in $schemaTable.Rows) {
        $tableName = $tblRow['TABLE_NAME']
        Write-Output "=== Content of Table: $tableName ==="
        $cmd = New-Object System.Data.OleDb.OleDbCommand("SELECT * FROM [$tableName]", $conn)
        $da = New-Object System.Data.OleDb.OleDbDataAdapter($cmd)
        $dt = New-Object System.Data.DataTable
        $da.Fill($dt) | Out-Null
        
        for ($r = 0; $r -lt $dt.Rows.Count; $r++) {
            $rowVals = @()
            for ($c = 0; $c -lt $dt.Columns.Count; $c++) {
                $rowVals += $dt.Rows[$r][$c].ToString()
            }
            Write-Output ($rowVals -join "`t")
        }
        Write-Output "========================================"
    }
    $conn.Close()
} catch {
    Write-Output "Error: $_"
}
```

#### File: `read_sample_users.ps1`
```powershell
$xlsxPath = "e:\works\sample users.xlsx"
$tempDir = "e:\works\xlsx_extracted_json"

if (Test-Path $tempDir) {
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
}

Copy-Item $xlsxPath "$tempDir.zip"
Expand-Archive -Path "$tempDir.zip" -DestinationPath $tempDir -Force
Remove-Item "$tempDir.zip" -ErrorAction SilentlyContinue

$sharedStrings = @()
$sharedStringsFile = Join-Path $tempDir "xl\sharedStrings.xml"
if (Test-Path $sharedStringsFile) {
    $xml = [xml](Get-Content $sharedStringsFile -Raw)
    $nodes = $xml.SelectNodes("//*[local-name()='t']")
    foreach ($n in $nodes) {
        $sharedStrings += $n.InnerText
    }
}

$sheetFile = Join-Path $tempDir "xl\worksheets\sheet1.xml"
if (-not (Test-Path $sheetFile)) {
    Write-Error "sheet1.xml not found"
    exit 1
}

$xml = [xml](Get-Content $sheetFile -Raw)
$ns = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
$ns.AddNamespace("x", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
$rows = $xml.SelectNodes("//x:sheetData/x:row", $ns)
if ($rows -eq $null -or $rows.Count -eq 0) {
    $rows = $xml.SelectNodes("//*[local-name()='row']")
}

function Get-ColLetter($cellRef) {
    if ($cellRef -match "^([A-Z]+)") {
        return $Matches[1]
    }
    return ""
}

$users = @{}

foreach ($row in $rows) {
    $rowNum = [int]$row.r
    if ($rowNum -eq 1) { continue }
    
    $cols = $row.SelectNodes("x:c", $ns)
    if ($cols -eq $null -or $cols.Count -eq 0) {
        $cols = $row.SelectNodes("*[local-name()='c']")
    }
    
    $rowData = @{}
    foreach ($col in $cols) {
        $colLetter = Get-ColLetter $col.r
        $val = ""
        $vNode = $col.SelectSingleNode("x:v", $ns)
        if ($vNode -eq $null) {
            $vNode = $col.SelectSingleNode("*[local-name()='v']")
        }
        if ($vNode -ne $null) {
            $val = $vNode.InnerText
            if ($col.t -eq "s") {
                $idx = [int]$val
                if ($idx -lt $sharedStrings.Count) {
                    $val = $sharedStrings[$idx]
                }
            }
        }
        $rowData[$colLetter] = $val.Trim()
    }
    
    $dopName = $rowData["B"]
    $dopPhone = $rowData["C"]
    $zoneName = $rowData["D"]
    $cgmName = $rowData["E"]
    $cgmPhone = $rowData["F"]
    $division = $rowData["G"]
    $gmName = $rowData["H"]
    $gmPhone = $rowData["I"]
    $circleName = $rowData["J"]
    $dgmName = $rowData["K"]
    $dgmPhone = $rowData["L"]
    $managerName = $rowData["M"]
    $managerPhone = $rowData["N"]
    $wardName = $rowData["O"]
    
    # 1. DOP
    if ($dopPhone) {
        if (-not $users.ContainsKey($dopPhone)) {
            $users[$dopPhone] = @{
                phoneNumber = $dopPhone
                name = $dopName
                designation = "Director of Project (DOP)"
                role = "DOP"
                locations = @()
            }
        }
        $loc = @{ corp = "Corporate"; zoneName = $null; division = $null; circleName = $null; wardName = $null; role = "DOP" }
        $exists = $false
        foreach ($l in $users[$dopPhone].locations) {
            if ($l.corp -eq "Corporate") { $exists = $true; break }
        }
        if (-not $exists) {
            $users[$dopPhone].locations += $loc
        }
    }
    
    # 2. CGM
    if ($cgmPhone) {
        if (-not $users.ContainsKey($cgmPhone)) {
            $users[$cgmPhone] = @{
                phoneNumber = $cgmPhone
                name = $cgmName
                designation = "Chief General Manager (CGM)"
                role = "CGM"
                locations = @()
            }
        }
        $loc = @{ corp = "MMC"; zoneName = $zoneName; division = $null; circleName = $null; wardName = $null; role = "CGM" }
        $exists = $false
        foreach ($l in $users[$cgmPhone].locations) {
            if ($l.corp -eq "MMC" -and $l.zoneName -eq $zoneName) { $exists = $true; break }
        }
        if (-not $exists) {
            $users[$cgmPhone].locations += $loc
        }
    }
    
    # 3. GM
    if ($gmPhone) {
        if (-not $users.ContainsKey($gmPhone)) {
            $users[$gmPhone] = @{
                phoneNumber = $gmPhone
                name = $gmName
                designation = "General Manager (GM)"
                role = "GM"
                locations = @()
            }
        }
        $loc = @{ corp = "MMC"; zoneName = $zoneName; division = $division; circleName = $null; wardName = $null; role = "GM" }
        $exists = $false
        foreach ($l in $users[$gmPhone].locations) {
            if ($l.corp -eq "MMC" -and $l.zoneName -eq $zoneName -and $l.division -eq $division) { $exists = $true; break }
        }
        if (-not $exists) {
            $users[$gmPhone].locations += $loc
        }
    }
    
    # 4. DGM
    if ($dgmPhone) {
        if (-not $users.ContainsKey($dgmPhone)) {
            $users[$dgmPhone] = @{
                phoneNumber = $dgmPhone
                name = $dgmName
                designation = "Deputy General Manager (DGM)"
                role = "DGM"
                locations = @()
            }
        }
        $loc = @{ corp = "MMC"; zoneName = $zoneName; division = $division; circleName = $circleName; wardName = $null; role = "DGM" }
        $exists = $false
        foreach ($l in $users[$dgmPhone].locations) {
            if ($l.corp -eq "MMC" -and $l.zoneName -eq $zoneName -and $l.division -eq $division -and $l.circleName -eq $circleName) { $exists = $true; break }
        }
        if (-not $exists) {
            $users[$dgmPhone].locations += $loc
        }
    }
    
    # 5. MANAGER
    if ($managerPhone) {
        if (-not $users.ContainsKey($managerPhone)) {
            $users[$managerPhone] = @{
                phoneNumber = $managerPhone
                name = $managerName
                designation = "Manager (AE)"
                role = "MANAGER"
                locations = @()
            }
        }
        $loc = @{ corp = "MMC"; zoneName = $zoneName; division = $division; circleName = $circleName; wardName = $wardName; role = "MANAGER" }
        $exists = $false
        foreach ($l in $users[$managerPhone].locations) {
            if ($l.corp -eq "MMC" -and $l.zoneName -eq $zoneName -and $l.division -eq $division -and $l.circleName -eq $circleName -and $l.wardName -eq $wardName) { $exists = $true; break }
        }
        if (-not $exists) {
            $users[$managerPhone].locations += $loc
        }
    }
}

$usersList = @()
foreach ($k in $users.Keys) {
    $usersList += $users[$k]
}

$json = ConvertTo-Json $usersList -Depth 5
$json | Out-File "e:\works\users_parsed.json" -Encoding utf8

Write-Output "Successfully parsed and saved $($users.Count) unique users to e:\works\users_parsed.json"

foreach ($k in $users.Keys) {
    $u = $users[$k]
    Write-Output "User: $($u.name) ($($u.phoneNumber)) - Role: $($u.role) - Locs: $($u.locations.Count)"
}

Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
```
