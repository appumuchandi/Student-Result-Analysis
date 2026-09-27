# College Result Analysis System

A full-stack web application for managing and analyzing student academic results. Built for Heads of Department (HOD) to upload semester results via Excel, with automated VTU credit mapping, SGPA/CGPA calculation, and role-based dashboards for HOD, Admin, and Students.

## Features

### Core Functionality
- **Excel Import Workflow** — HODs upload official semester result sheets (.xls/.xlsx) with preview-before-confirm workflow
- **VTU 2022 Scheme Credit Mapping** — 18 official subject codes mapped to credits (1/3/4); zero-credit subjects (BPEK359, BYOK459) excluded from SGPA/CGPA
- **CreditResolver** — Centralized credit lookup (database-first with in-memory fallback) ensuring consistent credits across import and recalculation
- **Transactional Import** — Preview → Confirm → Import with detailed sync summary (new/updated/unchanged students & subject results)
- **Automatic SGPA/CGPA Recalculation** — Credit-weighted calculations persisted to database on every import

### Role-Based Access
| Role | Capabilities |
|------|--------------|
| **HOD** | Excel upload, department overview, performance analytics, Quick Access (Import, Rank Analysis) |
| **Admin** | System oversight, upload history, user management |
| **Student** | Personal result view (SGPA, CGPA, subject-wise marks, backlog status), semester filtering |

### Analytics & Export
- Department overview: total students, pass/fail counts, average SGPA/percentage, total backlogs
- Subject-wise performance: average marks, pass percentage
- Rank analysis with filtering (branch, batch, semester, category)
- Export to Excel (.xlsx via Apache POI) and PDF (OpenPDF)

### Security
- BCrypt password hashing
- HttpSession-based authentication (JSESSIONID)
- Role-based endpoint authorization (HOD/ADMIN/STUDENT)
- Forgot password with OTP flow

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Spring Boot 4.1.0, Java 17, Spring Data JPA, Spring Security Crypto |
| **Database** | MySQL 8 (production), H2 (test) |
| **Build** | Maven |
| **Frontend** | Vanilla HTML/CSS/JS (ES6 modules), served via Python HTTP server |
| **Libraries** | Apache POI 5.4.1 (Excel), OpenPDF 1.3.35 (PDF) |

## Project Structure

```
College_Result_Analysis_Project/
├── Backend_Development/
│   └── Result_Analysis/
│       ├── src/main/java/com/Result_Analysis/Result_Analysis/
│       │   ├── controller/          # REST endpoints
│       │   ├── service/             # Business logic
│       │   ├── repository/          # JPA repositories
│       │   ├── entity/              # JPA entities
│       │   ├── dto/                 # Request/Response DTOs
│       │   ├── CreditResolver.java  # Centralized VTU credit lookup
│       │   ├── ExcelImportService.java
│       │   └── StudentService.java  # SGPA/CGPA calculation
│       └── src/test/                # 19 tests (11 CreditResolver + 7 integration + 1 context)
│
├── Frontend_Development/
│   ├── dashboard.html               # Main role-aware dashboard
│   ├── import_results.html          # HOD Excel upload page
│   ├── rank_analysis.html           # Rank analysis & export
│   ├── home.html                    # Student lookup landing page
│   ├── index.html                   # Login page
│   ├── script_dashboard.js          # Dashboard logic (role-first init)
│   ├── script_import.js             # Import workflow (preview/confirm)
│   ├── script_Rank_Analysis.js      # Rank analysis logic
│   ├── styles.css                   # Responsive design (fixed sidebar, mobile hamburger)
│   └── config.js                    # API_BASE_URL
```

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- MySQL 8+
- Python 3.x (for frontend server)

### Backend Setup
```bash
cd Backend_Development/Result_Analysis

# Configure database in src/main/resources/application.properties
# spring.datasource.url=jdbc:mysql://localhost:3306/result_analysis_fresh
# spring.datasource.username=root
# spring.datasource.password=your_password

# Run tests
./mvnw clean test

# Build JAR
./mvnw package -DskipTests

# Start server (port 8081)
java -jar target/Result_Analysis-0.0.1-SNAPSHOT.jar
```

### Frontend Setup
```bash
cd Frontend_Development

# Start static server (port 5500)
python -m http.server 5500
```

### Access Application
- **Frontend**: http://localhost:5500
- **Backend API**: http://localhost:8081

### Default Credentials
| Role | Email / User ID | Password |
|------|-----------------|----------|
| HOD | sangitarw@klecet.edu.in | 9029457217 |
| HOD (legacy) | hod | hod123 |
| Admin | admin | admin123 |

## API Endpoints

### Authentication
| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/login` | Public | Login, returns session cookie |
| POST | `/api/auth/logout` | Authenticated | Invalidate session |
| GET | `/api/auth/me` | Authenticated | Current user info + role |
| POST | `/api/auth/forgot-password` | Public | Request OTP |
| POST | `/api/auth/verify-otp` | Public | Verify OTP |
| POST | `/api/auth/reset-password` | Public | Reset password |
| POST | `/api/auth/change-password` | Authenticated | Change password (student initial) |

### Student Management
| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| GET | `/students` | HOD, ADMIN | List all students |
| GET | `/students/usn/{usn}` | HOD, ADMIN, STUDENT | Get student by USN |
| GET | `/students/analytics/subject-stats` | HOD, ADMIN | Subject-wise performance |
| GET | `/students/analytics/dept-stats` | HOD, ADMIN | Department overview stats |

### Excel Import (HOD only)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/import/preview` | Upload Excel, return preview (no DB writes) |
| POST | `/api/import/confirm` | Confirm preview, write transactionally |
| GET | `/api/import/history/latest` | Latest upload metadata |

### Export & Rank
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/export/excel` | Export filtered results to .xlsx |
| GET | `/api/export/pdf` | Export filtered results to .pdf |
| GET | `/api/rank-analysis` | Rank data with filters |

## Database Schema (Key Tables)

```sql
-- Students
CREATE TABLE students (
    usn VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100), email VARCHAR(100), phone VARCHAR(15),
    branch VARCHAR(10), semester INT, academic_year VARCHAR(20),
    college_code VARCHAR(10), password_hash VARCHAR(255),
    role ENUM('STUDENT','HOD','ADMIN'), must_change_password BOOLEAN,
    sgpa DECIMAL(4,2), cgpa DECIMAL(4,2), percentage DECIMAL(5,2),
    result ENUM('PASS','FAIL'), backlog INT DEFAULT 0
);

-- Subject Results
CREATE TABLE subject_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_usn VARCHAR(20), subject_code VARCHAR(20), subject_name VARCHAR(100),
    credits INT, marks INT, re_val CHAR(1), grade_point DECIMAL(3,2),
    grade VARCHAR(2), status ENUM('PASS','FAIL'), semester INT,
    FOREIGN KEY (student_usn) REFERENCES students(usn)
);

-- VTU Subject Credits (2022 Scheme)
CREATE TABLE subject_credits (
    subject_code VARCHAR(20) PRIMARY KEY,
    subject_name VARCHAR(100), credits INT NOT NULL
);

-- Upload History
CREATE TABLE excel_upload_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255), branch VARCHAR(10), semester INT, batch VARCHAR(20),
    uploaded_by VARCHAR(100), uploaded_at TIMESTAMP,
    students_imported INT, subject_results_imported INT
);
```

## VTU 2022 Scheme Credit Mapping

| Subject Code | Credits | Subject Code | Credits |
|--------------|---------|--------------|---------|
| BPHYS102 / BPHYS202 | 4 | BMATS101 / BMATS201 | 4 |
| BCHES102 / BCHES202 | 4 | BESCK104B / BESCK204B | 3 |
| BPLCK105B / BPLCK205B | 1 | BENGK106 / BENGK206 | 1 |
| BKSKK107 / BKBKK107 | 1 | BIDTK158 / BIDTK258 | 1 |
| BPEK359 / BYOK459 | **0** (excluded from SGPA/CGPA) | BMATE301 / BMATE401 | 3 |
| UHVE322 / UHVE422 | 1 | BPOG301 | 3 |

*18 total mappings loaded via `SubjectCreditInitializer` on startup.*

## SGPA / CGPA Calculation

- **SGPA** = Σ(Grade Point × Credits) / Σ(Credits) for semester
- **CGPA** = Weighted average of all semester SGPAs by credits
- **Zero-credit subjects** (BPEK359, BYOK459) excluded from both calculations
- Recalculated automatically on every confirmed Excel import
- Persisted to `students.sgpa`, `students.cgpa`

## Configuration

### Backend (`application.properties`)
```properties
server.port=8081
spring.datasource.url=jdbc:mysql://localhost:3306/result_analysis_fresh
spring.datasource.username=root
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
spring.web.cors.allowed-origins=http://localhost:5500
```

### Frontend (`config.js`)
```javascript
window.APP_CONFIG = {
    API_BASE_URL: "http://localhost:8081"
};
```

## Testing

```bash
cd Backend_Development/Result_Analysis
./mvnw clean test
```

**Test Results**: 19 tests passing
- 11 `CreditResolverTest` — credit lookup, fallback, edge cases
- 7 `ExcelExportIntegrationTest` — export functionality
- 1 `ResultAnalysisApplicationTests` — context loads

## Deployment Notes

1. **Production DB**: Use MySQL with `result_analysis_fresh` schema
2. **CORS**: Configure `spring.web.cors.allowed-origins` for your frontend domain
3. **Session**: HttpSession with JSESSIONID (configure `server.servlet.session.timeout` as needed)
4. **File Upload**: Max 20MB Excel files (configurable in `ExcelImportController`)
5. **Reverse Proxy**: Recommended (nginx) for production with HTTPS

## License

MIT License — see LICENSE file for details.

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'feat: add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

**Built with** Spring Boot 4.1.0 • Java 17 • MySQL • Apache POI • OpenPDF