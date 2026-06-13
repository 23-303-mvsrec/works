# Codebase Diagrams and Architecture Documentation

This document provides a comprehensive view of the **HMWSSB Works Measurement and Estimation System** architecture, data flow, database schema, and class hierarchy.

---

## 1. High-Level Architecture Diagram
The application follows a **monolithic client-server architecture** using an **AngularJS** SPA (Single Page Application) frontend connected via REST endpoints to a **Spring Boot** backend, which persists data to a **PostgreSQL** database.

```mermaid
graph TD
    subgraph Client ["Client Side (Browser)"]
        style Client fill:#f5f5e3,stroke:#c8c87a,stroke-width:2px
        A["index.html (Measurement Sheet)"]
        B["abstract.html (General Abstract)"]
        C["hierarchy.js (Cascading Ward Data)"]
        D["AngularJS Controllers"]
        E["localStorage (Temporary State Store)"]
    end

    subgraph Server ["Backend (Spring Boot REST API)"]
        style Server fill:#e8f4fb,stroke:#7fa8c0,stroke-width:2px
        F["WorksApplication (Main App)"]
        G["HomeController (Default View Route)"]
        H["ItemController (Autocomplete & Search)"]
        I["EstimateController (CRUD Operations)"]
        
        J["ItemRepository"]
        K["EstimateRepository"]
    end

    subgraph DB ["Database (PostgreSQL)"]
        style DB fill:#eafaf1,stroke:#27ae60,stroke-width:2px
        L[("estimates table")]
        M[("estimate_items table")]
        N[("itemlist table (Master data)")]
    end

    A --> D
    B --> D
    C --> D
    D --> E
    D -.->|HTTP JSON API| Server
    
    H --> J
    I --> K
    
    J --> N
    K --> L
    K --> M
    L --- M
```

---

## 2. Database Schema (Entity-Relationship Diagram)
The database persists estimates and their dynamic list of estimate items. The system also reads from a static/master `itemlist` table for autocomplete entries.

```mermaid
erDiagram
    estimates {
        INTEGER id PK "IDENTITY (AUTO_INCREMENT)"
        VARCHAR(500) name_of_work "Description of the project"
        DOUBLE gst_percent "GST percentage applied"
        DOUBLE grand_total "Grand total amount in Rs."
        TIMESTAMP created_at "Autopopulated creation timestamp"
        DOUBLE unforeseen_amount "Part-III LS unforeseen items amount"
        VARCHAR(100) corp "Municipal Corporation name"
        VARCHAR(200) zone_name "Zone name"
        VARCHAR(100) division "Division name"
        VARCHAR(200) circle_name "Circle number/name"
        VARCHAR(200) ward_name "Ward number/name"
    }
    
    estimate_items {
        INTEGER id PK "IDENTITY (AUTO_INCREMENT)"
        INTEGER estimate_id FK "Cascade Delete on Parent Removal"
        INTEGER sno "Sequence number of row"
        VARCHAR(10) is_material "Material toggle ('Yes'/'No')"
        TEXT description "Item description text"
        DOUBLE num "Multiplier / count"
        DOUBLE length "Length measurement"
        DOUBLE breadth "Breadth measurement"
        DOUBLE depth "Depth/Height measurement"
        DOUBLE quantity "Calculated quantity"
        DOUBLE rate "Unit rate"
        VARCHAR(100) unit "Unit of measurement"
        DOUBLE amount "Row amount (quantity * rate)"
    }

    itemlist {
        INTEGER slno PK "Serial number"
        TEXT item_description "Description of standard item"
        VARCHAR(50) unit "Standard unit"
        NUMERIC rate "Standard rate"
    }

    estimates ||--o{ estimate_items : "has (One-To-Many / Cascade)"
```

---

## 3. Class Diagram (Backend Java Structure)
This class diagram details the fields, repositories, and endpoints of the Spring Boot application.

```mermaid
classDiagram
    class WorksApplication {
        +main(args: String[]) void
    }

    class HomeController {
        +home() ResponseEntity~String~
    }

    class ItemController {
        -itemRepository: ItemRepository
        +search(q: String) ResponseEntity~List~Item~~
    }

    class EstimateController {
        -estimateRepository: EstimateRepository
        +save(estimate: Estimate) ResponseEntity~Estimate~
        +list() ResponseEntity~List~Estimate~~
        +get(id: Integer) ResponseEntity~Estimate~
        +delete(id: Integer) ResponseEntity~Void~
    }

    class Item {
        -slno: Integer
        -itemDescription: String
        -unit: String
        -rate: Double
        +getters_setters()
    }

    class Estimate {
        -id: Integer
        -nameOfWork: String
        -gstPercent: Double
        -grandTotal: Double
        -createdAt: LocalDateTime
        -unforeseenAmount: Double
        -corp: String
        -zoneName: String
        -division: String
        -circleName: String
        -wardName: String
        -items: List~EstimateItem~
        #onCreate() void
        +getters_setters()
    }

    class EstimateItem {
        -id: Integer
        -sno: Integer
        -isMaterial: String
        -description: String
        -num: Double
        -length: Double
        -breadth: Double
        -depth: Double
        -quantity: Double
        -rate: Double
        -unit: String
        -amount: Double
        +getters_setters()
    }

    class ItemRepository {
        <<interface>>
        +searchByDescription(query: String) List~Item~
    }

    class EstimateRepository {
        <<interface>>
    }

    ItemController --> ItemRepository : uses
    EstimateController --> EstimateRepository : uses
    ItemRepository ..> Item : queries
    EstimateRepository ..> Estimate : queries
    Estimate *--> EstimateItem : aggregates
```

---

## 4. Sequence Diagram (Measurement to Abstract & Save Flow)
This diagram illustrates the lifecycle of generating and saving an estimate, starting with selection and row inputs on `index.html` all the way to DB persistence on `abstract.html`.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as Browser UI (HTML)
    participant Angular as AngularJS Controllers
    participant API as Spring Boot REST Controller
    participant DB as PostgreSQL Database

    %% Step 1: Dropdown Selection
    User->>UI: Select CORP / Zone / Division / Circle / Ward
    UI->>Angular: Triggers onCascadeChange()
    Angular->>UI: Populate dependent dropdown lists (via WARD_HIERARCHY)

    %% Step 2: Search and Autocomplete
    User->>UI: Type search query in Description field
    UI->>Angular: Keyup Event (Debounced 300ms)
    Angular->>API: GET /api/items/search?q={query}
    API->>DB: Query public.itemlist via Specifications/LIKE
    DB-->>API: List of matching Items
    API-->>Angular: HTTP 200 OK (JSON Array of Items)
    Angular->>UI: Show autocomplete suggestions dropdown

    %% Step 3: Selection & Row Calculation
    User->>UI: Select an item from dropdown
    UI->>Angular: selectItem(row, item)
    Angular->>Angular: Populate rate, unit, and computeRow()
    Angular->>UI: Display computed quantities & amounts in row

    %% Step 4: Redirection and LocalStorage
    User->>UI: Click "Generate Estimate" button
    UI->>Angular: saveEstimate()
    Angular->>Angular: Partition costs into Civil vs. Material
    Angular->>UI: Save 'current_estimate_data' to localStorage
    Angular->>UI: Redirect to abstract.html

    %% Step 5: Abstract Load & Persistence
    UI->>Angular: Read 'current_estimate_data' from localStorage
    Angular->>UI: Render Part-I, II, and III totals & Total in Words
    User->>UI: Modify unforeseen items / round off, click "Save Estimate"
    UI->>Angular: saveEstimateToDb()
    Angular->>API: POST /api/estimates (Payload with Estimate & items)
    API->>DB: save(Estimate) entity (cascade persist items)
    DB-->>API: Persisted entity with generated ID
    API-->>Angular: HTTP 200 OK (Saved Estimate JSON)
    Angular->>UI: Update localStorage with saved ID & alert success
```
