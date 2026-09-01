# OpenAttend System Architecture & Database Schema

OpenAttend is an architecture-decoupled, read-only companion attendance platform tailored for educational institutions.

---

## 🏗 System Architecture Diagram

```mermaid
graph TD
    Faculty[Faculty Google Forms] -->|Marks Attendance| GoogleSheet[Google Spreadsheet]
    
    subgraph OpenAttend Core Backend (Java Spring Boot 3)
        SheetsClient[SheetsClient - Google Sheets Java SDK] -->|Polls Worksheet Values| SyncDiffer[SyncDiffer - SHA-256 Hashing]
        SyncDiffer -->|Unchanged Hash| Skip[SKIPPED_NO_CHANGE]
        SyncDiffer -->|New/Modified Hash| UpsertEngine[UpsertEngine - @Transactional JPA Batch]
        UpsertEngine -->|Atomic Upsert| PostgreSQL[(PostgreSQL Database via Flyway)]
        UpsertEngine -->|Log Run Status| SyncLogger[SyncLogger - Audit Log]
        
        Scheduler[Spring TaskScheduler / Cron] -->|Scheduled Polling| SheetsClient
        PredictorCore[PredictorService Engine] -->|Calculates Safe Skips & Projections| RESTApi
        Security[Spring Security 6 + JJWT] -->|Stateless Auth & Domain Filter| RESTApi[Spring Web MVC REST Controllers]
        Actuator[Spring Boot Actuator] -->|Health & Prometheus Metrics| Metrics[/actuator/prometheus]
    end

    GoogleSheet -->|ReadOnly Scoped Access| SheetsClient

    subgraph Client Experience Layer
        PostgreSQL -->|Reads Only| RESTApi
        RESTApi -->|Serves JSON API & Dashboard| WebUI[Student & Admin PWA Dashboard]
    end
```

---

## 🗄 Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    USER ||--o{ REFRESH_TOKEN : owns
    USER ||--o{ ATTENDANCE_RECORD : has
    STUDENT ||--o{ ATTENDANCE_RECORD : tracks
    SUBJECT ||--o{ ATTENDANCE_RECORD : contains
    ATTENDANCE_RECORD ||--o{ ATTENDANCE_HISTORY_EVENT : logs
    WORKSHEET_MAPPING ||--o{ SYNC_LOG : monitors

    USER {
        string id PK
        string email UK
        string passwordHash
        enum role "STUDENT | ADMIN | SUPER_ADMIN"
        boolean isActive
        datetime createdAt
        datetime updatedAt
    }

    STUDENT {
        string id PK
        string userId FK
        string rollNo UK
        string name
        string division
        string batch
        datetime createdAt
        datetime updatedAt
    }

    SUBJECT {
        string id PK
        string code UK
        string name
        int totalPlanned
        datetime createdAt
        datetime updatedAt
    }

    WORKSHEET_MAPPING {
        string id PK
        string subjectId FK
        string sheetId
        string worksheetName
        string range
        json columnRoles
        boolean isActive
        datetime createdAt
        datetime updatedAt
    }

    ATTENDANCE_RECORD {
        string id PK
        string studentId FK
        string subjectId FK
        date lectureDate
        int sessionIndex
        enum status "PRESENT | ABSENT | NA"
        string faculty
        string remarks
        string sourceRowHash
        datetime syncedAt
        datetime updatedAt
    }

    ATTENDANCE_HISTORY_EVENT {
        string id PK
        string attendanceRecordId FK
        enum previousStatus "PRESENT | ABSENT | NA"
        enum newStatus "PRESENT | ABSENT | NA"
        datetime changedAt
        string syncLogId FK
    }

    NOTIFICATION {
        string id PK
        string studentId FK
        string subjectId FK
        string type
        string message
        boolean isRead
        string syncLogId
        datetime createdAt
    }

    SYNC_LOG {
        string id PK
        string worksheetMappingId FK
        enum status "SUCCESS | SKIPPED_NO_CHANGE | PARTIAL_FAILURE | FAILED"
        int rowsRead
        int rowsUpserted
        string contentHash
        string errorMessage
        datetime startedAt
        datetime finishedAt
        int durationMs
    }
```

---

## 🛡 Architectural Guarantees & Non-Negotiable Invariants

1. **Strict Read-Only Google Sheets Scopes**: No code path anywhere requests Google write permissions (`spreadsheets.readonly` only).
2. **Zero Synchronous External Calls on User Reads**: Student and Admin dashboard reads query PostgreSQL directly; they never call Google Sheets API synchronously.
3. **Natural Key Idempotency**: Attendance records are uniquely identified by `(studentId, subjectId, lectureDate, sessionIndex)`. Repeated sync runs produce zero duplicate rows.
4. **Institutional Security & RBAC**: Strict validation that all authenticating accounts belong to the allowed institutional domain (`@ves.ac.in`).
