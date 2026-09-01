<p align="center"> 
  <br/>
  <a href="https://github.com/iykyk-vedant/OpenAttend-VESIT/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg?color=3F51B5&style=for-the-badge&label=License&logoColor=000000&labelColor=ececec" alt="License: MIT"></a>
  <a href="https://github.com/iykyk-vedant/OpenAttend-VESIT/actions/workflows/ci.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/iykyk-vedant/OpenAttend-VESIT/ci.yml?branch=main&label=CI%20Pipeline&logo=github&style=for-the-badge&logoColor=000000&labelColor=ececec" alt="CI Pipeline"/>
  </a>
  <br/>
  <br/>
</p>

<p align="center">
  <h1 align="center">OpenAttend — VESIT</h1>
</p>
<h3 align="center">High performance live companion attendance platform for VESIT</h3>
<p align="center"><em>Built with Java 21 LTS, Spring Boot 3.4, PostgreSQL, Flyway, and Vanilla PWA</em></p>
<br/>

> [!IMPORTANT]
> ⚠️ **OpenAttend** is an architecture-decoupled, read-only companion attendance platform tailored for VESIT. Faculty mark attendance via Google Forms into Google Sheets while OpenAttend normalizes and syncs data into PostgreSQL in real time!

> [!NOTE]
> Detailed technical specs, database schemas, and administrator guides can be found in [`docs/`](docs/).

---

## 🏗 Architecture & Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend Framework** | **Java 21 LTS** + **Spring Boot 3.4.x** (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`) |
| **Database & Migrations** | **PostgreSQL** + **Flyway Migrations** (`V1__init_schema.sql`) |
| **Security & Auth** | **Spring Security 6** + **Stateless JJWT** (`@ves.ac.in` email restriction, BCrypt hashing) |
| **Sync Engine** | **Google Sheets API Java SDK** (`spreadsheets.readonly`) + SHA-256 Differ + `@Transactional` Upsert |
| **Observability** | **Spring Boot Actuator** (`/actuator/health`, `/actuator/prometheus`) |
| **Frontend UI** | **Vanilla HTML5/CSS3 PWA** with Service Worker (`sw.js`) and Offline Caching |
| **Containerization** | Multi-stage **Dockerfile** + **Docker Compose** |

---

## ⚡ Quick Start (Local Setup)

### Prerequisites
- **Java 21 LTS** (OpenJDK or Eclipse Temurin)
- **Apache Maven 3.9+** (or `./mvnw`)
- **Docker & Docker Compose** (for PostgreSQL)

### 1. Clone & Configure
```bash
git clone https://github.com/iykyk-vedant/OpenAttend-VESIT.git
cd OpenAttend-VESIT
cp .env.example .env
```

### 2. Start PostgreSQL
```bash
docker compose -f infra/docker/docker-compose.yml up -d postgres
```

### 3. Run Spring Boot Backend
```bash
cd backend
mvn spring-boot:run
```

Open **[http://localhost:3000](http://localhost:3000)** in your browser.

---

## 🧪 Testing & Verification

Run the full automated test suite (Unit, Predictor math, Idempotent sync differ, Auth, Admin RBAC, and MockMvc endpoints):
```bash
cd backend
mvn test
```

---

## 🐳 Docker Deployment

To build and run both the Spring Boot application and PostgreSQL container together:
```bash
docker compose -f infra/docker/docker-compose.yml up --build -d
```

---

## 📋 Features Matrix

| Features | Student | Admin / Faculty |
| :--- | :---: | :---: |
| **Real-time Read-Only Google Sheets Sync** | Yes | Yes |
| **SHA-256 Natural Key Idempotency (Zero Duplicates)** | Yes | Yes |
| **Attendance Predictor (Safe Skips & Must-Attend Math)** | Yes | Yes |
| **Threshold Breach & Defaulter Alerts (<75%)** | Yes | Yes |
| **Filterable History & Roll-Call CSV Export** | Yes | Yes |
| **Admin Cold-Start Setup (Sheet Connect ➔ Map ➔ Sync)** | No | Yes |
| **Roster CSV Import with Pre-Commit Diffing** | No | Yes |
| **Prometheus Metrics & Health Actuator** | No | Yes |

---

## 📄 Documentation Links
- [**PRD & Requirements Specification**](docs/PRD.md)
- [**System Architecture & ER Diagram**](docs/architecture.md)
- [**Administrator Setup Guide**](docs/setup-guide.md)
- [**Milestone Roadmap (M0–M9)**](docs/Milestone.md)
- [**Autonomous Agent Protocol (AGENTS.md)**](AGENTS.md)
