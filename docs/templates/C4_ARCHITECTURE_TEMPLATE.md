# C4 Architecture Model Template

*Based on Simon Brown's C4 Model for Visualizing Software Architecture. Use Mermaid.js for diagrams.*

## 1. System Context (Level 1)
**Purpose**: Shows the software system you are building and how it fits into the world in terms of the people who use it and the other software systems it interacts with.

```mermaid
C4Context
    title System Context diagram for [System Name]
    
    Person(user, "User", "A user of the system")
    System(system, "[System Name]", "The core system doing the thing")
    System_Ext(external, "External System", "An external service we depend on")
    
    Rel(user, system, "Uses")
    Rel(system, external, "Gets data from", "HTTPS")
```

## 2. Container (Level 2)
**Purpose**: Zooms into the software system, showing the high-level technical building blocks (containers) and how they interact. A "container" is a web application, mobile app, database, file system, etc.

```mermaid
C4Container
    title Container diagram for [System Name]
    
    Person(user, "User", "A user of the system")
    
    System_Boundary(c1, "[System Name]") {
        Container(webapp, "Web Application", "SvelteKit, TS", "Delivers the SPA and handles routing")
        Container(api, "API Server", "Node.js, Express", "Provides functionality via JSON/HTTPS")
        ContainerDb(db, "Database", "PostgreSQL", "Stores user profiles and application state")
    }
    
    Rel(user, webapp, "Visits", "HTTPS")
    Rel(webapp, api, "Makes API calls to", "JSON/HTTPS")
    Rel(api, db, "Reads from and writes to", "SQL")
```

## 3. Component (Level 3)
**Purpose**: Zooms into an individual container to show the components inside it.

```mermaid
C4Component
    title Component diagram for [Container Name]
    
    Container(spa, "Single Page App", "Svelte", "The frontend application")
    
    Container_Boundary(api, "API Application") {
        Component(auth, "Auth Controller", "Express Middleware", "Handles JWT validation")
        Component(feature, "Feature Service", "TypeScript Class", "Business logic for the feature")
        Component(repo, "Data Repository", "TypeORM", "Abstracts database access")
    }
    
    Rel(spa, auth, "Makes requests to", "JSON/HTTPS")
    Rel(auth, feature, "Authorizes and passes to")
    Rel(feature, repo, "Uses for data access")
```

## 4. Code / Data flow (Level 4 - Optional)
**Purpose**: Zooms into an individual component to show how it works (e.g., Sequence diagrams, class diagrams, state machines).

```mermaid
sequenceDiagram
    participant U as User
    participant C as Component
    participant S as Service
    
    U->>C: Action
    C->>S: Request Data
    S-->>C: Return Data
    C-->>U: Update UI
```
