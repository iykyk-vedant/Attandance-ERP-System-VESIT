# OpenAttend System Architecture & Database Schema

OpenAttend is an architecture-decoupled, read-only companion attendance platform tailored for educational institutions.

---

## 🏗 System Architecture Diagram

```mermaid
graph TD
    Faculty[Faculty Google Forms] -->|Marks Attendance| GoogleSheet[Google Spreadsheet]
    
    subgraph OpenAttend Core Engine
        SheetsClient[SheetsClient - Read-Only API] -->|Polls Worksheet Values| SyncDiffer[SyncDiffer - SHA-256 Hashing]
        SyncDiffer -->|Unchanged Hash| Skip[SKIPPED_NO_CHANGE]
        SyncDiffer -->|New/Modified Hash| UpsertEngine[UpsertEngine - Natural Key Transaction]
        UpsertEngine -->|Atomic Upsert| PostgreSQL[(PostgreSQL Database)]
        UpsertEngine -->|Log Run Status| SyncLogger[SyncLogger - Audit Log]
    end

    GoogleSheet -->|ReadOnly Access| SheetsClient

    subgraph Client Experience Layer
        PostgreSQL -->|Reads Only| RESTApi[Node.js REST API]
        RESTApi -->|Serves Dashboard| WebUI[Student & Admin Web Dashboard]
        PredictorCore[Predictor Core Engine] -->|Calculates Safe Skips| RESTApi
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
        string passHash
        string name
        enum role "STUDENT | ADMIN | SUPER_ADMIN"
        string rollNo
        string division
        string batch
    }

    ATTENDANCE_RECORD {
        string id PK
        string studentId FK
        string subjectId FK
        date lectureDate
        int sessionIndex
        enum status "PRESENT | ABSENT | EXCUSED"
        string sourceRowHash
    }

    ATTENDANCE_HISTORY_EVENT {
        string id PK
        string attendanceRecordId FK
        string previousStatus
        string newStatus
        string modifiedBy
        datetime timestamp
    }

    WORKSHEET_MAPPING {
        string id PK
        string subjectCode
        string worksheetTab
        json columnRoles
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
    }
```
