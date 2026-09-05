<p align="center"> 
  <br/>
  <a href="https://github.com/iykyk-vedant/Attandance-ERP-System-VESIT/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg?color=3F51B5&style=for-the-badge&label=License&logoColor=000000&labelColor=ececec" alt="License: MIT"></a>
  <a href="https://github.com/iykyk-vedant/Attandance-ERP-System-VESIT/actions/workflows/ci.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/iykyk-vedant/Attandance-ERP-System-VESIT/ci.yml?branch=main&label=CI%20Pipeline&logo=github&style=for-the-badge&logoColor=000000&labelColor=ececec" alt="CI Pipeline"/>
  </a>
  <a href="https://github.com/iykyk-vedant/Attandance-ERP-System-VESIT/issues">
    <img src="https://img.shields.io/github/issues/iykyk-vedant/Attandance-ERP-System-VESIT?style=for-the-badge&color=008672&label=Open%20Issues" alt="Open Issues"/>
  </a>
  <br/>
</p>

<p align="center">
  <h1 align="center">OpenAttend — VESIT Attendance ERP System</h1>
</p>
<h3 align="center">Next-generation, high-performance attendance platform for Vivekanand Education Society's Institute of Technology (VESIT)</h3>
<p align="center"><em>Built with Java 21 LTS, Spring Boot 3.4, PostgreSQL, Flyway, and Vanilla PWA</em></p>
<br/>

> [!IMPORTANT]
> **OpenAttend** is an open-source campus attendance and academic ERP system designed specifically for the division, batch, and elective workflow at VESIT. Teachers mark attendance in under 10 seconds via **"Mark Absentees Only"** mode, while students track real-time percentages and **Safe Skips** on their phones.

---

## 🌟 Key Highlights

### 👨‍🏫 For Faculty & Coordinators
- **10-Second Attendance Marking**: Defaults to "All Present" — faculty only tap the 2–4 absent roll numbers.
- **1-Click Defaulter List Generator**: Export institutional, notice-board-ready PDF and Excel lists for students with `< 75%` attendance.
- **NAAC/NBA Course Teaching Diary**: Automatically logs lecture dates, turnouts, and "Topics Covered" for accreditation files.
- **48-Hour Correction Window**: Protects academic integrity with a mandatory justification audit trail.

### 🎓 For Students
- **Visual Attendance Ring**: Animated SVG percentage ring color-coded (Green $\ge 75\%$, Amber $65-74.9\%$, Red $< 65\%$).
- **Smart Safe Skips Predictor**: *"You have 84% in Cloud Computing. You can safely skip 2 lectures and remain $\ge 75\%$."*
- **Recovery Target Math**: *"You have 68% in DMBI. Attend the next 3 consecutive lectures to cross 75%."*
- **Threshold Alerts**: Instant email alerts the moment your attendance drops below 75%.

---

## 🏗 Architecture & Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend Framework** | **Java 21 LTS** + **Spring Boot 3.4.x** (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`) |
| **Database & Migrations** | **PostgreSQL 16** + **Flyway Migrations** (`V1__init_schema.sql`) |
| **Security & Auth** | **Spring Security 6** + **Stateless JJWT** (`@ves.ac.in` domain check, BCrypt password hashing) |
| **Observability** | **Spring Boot Actuator** (`/actuator/health`, `/actuator/prometheus`) |
| **Frontend UI** | **Vanilla HTML5/CSS3 PWA** with Service Worker (`sw.js`) and Offline Caching |
| **Containerization** | Multi-stage **Dockerfile** + **Docker Compose** |

---

## 🤝 Open Source Contributor Roadmap

We welcome contributions from students, alumni, and developers! We have broken down the system into **15 structured GitHub issues** across 4 milestones:

- 🎯 **[Browse All Open Issues](https://github.com/iykyk-vedant/Attandance-ERP-System-VESIT/issues)**
- 🗂️ **[View The 4 Milestones](https://github.com/iykyk-vedant/Attandance-ERP-System-VESIT/milestones)**
- 🌟 Look for the **`good first issue`** label for beginner-friendly tasks!

---

## ⚡ Quick Start (Local Setup)

### Prerequisites
- **Java 21 LTS** (OpenJDK or Eclipse Temurin)
- **Apache Maven 3.9+** (or `./mvnw`)
- **Docker & Docker Compose**

### 1. Clone the Repository
```bash
git clone https://github.com/iykyk-vedant/Attandance-ERP-System-VESIT.git
cd Attandance-ERP-System-VESIT
```

### 2. Start PostgreSQL Database
```bash
docker compose up -d postgres
```

### 3. Run the Backend
```bash
cd backend
mvn spring-boot:run
```
Open **[http://localhost:8080](http://localhost:8080)** in your browser.

---

## 🧪 Testing

Run the full automated test suite (Predictor math, Security RBAC matrix, and API endpoints):
```bash
cd backend
mvn clean test
```

---

## 📄 Documentation Links
- [**Contributing Guidelines (CONTRIBUTING.md)**](CONTRIBUTING.md)
- [**PRD & Requirements Specification**](docs/PRD.md)
- [**System Architecture & ER Diagram**](docs/architecture.md)
- [**Administrator Setup Guide**](docs/setup-guide.md)
- [**Milestone Roadmap (M0–M9)**](docs/Milestone.md)
