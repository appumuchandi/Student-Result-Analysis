# Result Analysis Backend

## Required
- Java 17+
- MySQL

## Before starting
1. Create database `result_analysis` in MySQL.
2. Open `src/main/resources/application.properties`.
3. Replace `YOUR_MYSQL_PASSWORD` with your MySQL password.
4. Start MySQL.

## Start backend (Windows PowerShell)
```powershell
cd Backend_Development\Result_Analysis
.\mvnw.cmd spring-boot:run
```

Backend: http://localhost:8081/
All students: http://localhost:8081/students
Find by USN: http://localhost:8081/students/usn/1KL24EC002

## Frontend
Open the Frontend_Development folder and start the HTML files with VS Code Live Server, or open `index.html` directly. The JavaScript calls `http://localhost:8081`.
## Rank Analysis features
- Topper list sorted by CGPA
- Rank change versus the previous successful load in the same browser
- Department-wise average CGPA chart
- Current vs previous rank comparison chart
- PDF download button using the browser Print / Save as PDF dialog

The rank history is intentionally stored in browser localStorage because the current database does not yet contain a historical-results table. For permanent semester-wise rank history, add a separate rank-history table later.
