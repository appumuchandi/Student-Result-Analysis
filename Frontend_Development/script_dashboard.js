const API_BASE = window.APP_CONFIG?.API_BASE_URL || "http://localhost:8081";

let studentData = null;
const $ = (id) => document.getElementById(id);

function set(id, v) {
    const el = document.getElementById(id);
    if (el) el.textContent = v ?? '--';
}
function showError(m) {
    set('studentName', 'Data unavailable');
    const t = document.getElementById('resultTable');
    if (t) t.innerHTML = `<tr><td colspan="8" class="empty">${m}</td></tr>`
}
function showAdminState(username){
    const isHod = username && username.toLowerCase().includes('hod');
    const role = isHod ? 'HOD Account' : 'Admin Account';
    set('studentName', role);
    set('studentUSN', username || '--');
    set('branch', '--');
    set('semester', '--');
    set('year', '--');
    set('profileEmail', '--');
    set('profilePhone', '--');
    set('sgpa', '--');
    set('cgpa', '--');
    set('percentage', '--');
    set('result', '--');
    set('backlog', '--');
    const t = document.getElementById('resultTable');
    if (t) t.innerHTML = `<tr><td colspan="8" class="empty">${role} — personal student result information is available only for a Student account. Use Home → Find Your Result to view a student.</td></tr>`;
}
async function loadStudent() {
    // Loading state
    set('studentName', 'Loading...');
    set('studentUSN', 'Loading...');
    set('branch', 'Loading...');
    set('semester', '--');
    set('year', '--');
    set('profileEmail', '--');
    set('profilePhone', '--');
    set('sgpa', '--');
    set('cgpa', '--');
    set('percentage', '--');
    set('result', '--');
    set('backlog', '0');
    const t0 = document.getElementById('resultTable');
    if (t0) t0.innerHTML = `<tr><td colspan="8" class="empty">Loading result...</td></tr>`;

    // 1. Check authenticated user (admin/hod) via session
    let authUser = null;
    try{
        const ar = await fetch(`${API_BASE}/api/auth/me`, { credentials: 'include' });
        if(ar.ok){
            const aj = await ar.json();
            authUser = aj.authenticatedUser || aj.userId || null;
            if(authUser) authUser = String(authUser).trim();
        }
    }catch(e){}

    const usn = localStorage.getItem('usn');
    const collegeCode = localStorage.getItem('collegeCode');
    const branchLS = localStorage.getItem('branch');
    const userId = localStorage.getItem('userId');

    // If authenticated as admin/hod (userId present and matches authUser), show admin state
    // Do NOT attempt to fetch student using admin username
    const isAdminLike = (authUser && (authUser.toLowerCase()==='admin' || authUser.toLowerCase()==='hod' || authUser.toLowerCase().includes('admin') || authUser.toLowerCase().includes('hod'))) ||
                        (userId && (userId.toLowerCase()==='admin' || userId.toLowerCase()==='hod'));
    // Also if usn looks like admin/hod and no valid student collegeCode/branch, treat as admin
    if(isAdminLike){
        // Verify that this user is not actually a student: try student lookup, if fails show admin
        // For admin, we directly show admin state without student fetch
        showAdminState(authUser || userId || 'Admin');
        return;
    }

    // For student: need usn/collegeCode/branch from Home flow
    if (!usn || !collegeCode || !branchLS) {
        // Check if authUser exists but is not admin -> maybe student USN is authUser? Try that fallback
        if(authUser && usn && authUser.toLowerCase()===usn.toLowerCase()){
            // Use authUser as USN but still need collegeCode/branch -> show error with guidance
            showError('Student details not found. Go back to Home and enter College Code / Branch / USN.');
            return;
        }
        // If we have authUser but not admin, and we have usn, try fetch
        // If no usn at all, it's likely admin without usn -> already handled, else show guidance
        if(!usn){
            showError('No student selected. For admin/HOD, Student Details shows Admin Account. For students, go to Home → Find Your Result.');
            return;
        }
        showError('Student details not found. Go back to Home.');
        return;
    }
    try {
        const r = await fetch(`${API_BASE}/students/usn/${encodeURIComponent(usn)}?collegeCode=${encodeURIComponent(collegeCode)}&branch=${encodeURIComponent(branchLS)}`);
        if (!r.ok) {
            // If 404 and authUser is admin-like, show admin state instead of error
            if(isAdminLike) { showAdminState(authUser||userId); return; }
            throw new Error('Student details not found');
        }
        const d = await r.json();
        studentData = d;
        set('studentName', d.name);
        set('studentUSN', d.usn);
        set('branch', d.branch);
        set('semester', d.semester);
        set('year', d.academicYear);
        set('profileEmail', d.email || '--');
        set('profilePhone', d.phoneNumber || 'Not provided');
        set('sgpa', d.sgpa);
        set('cgpa', d.cgpa);
        set('percentage', d.percentage != null ? `${d.percentage}%` : null);
        set('backlog', d.backlog ?? 0);
        set('result', d.result);
        const semesterSelectEl = document.getElementById("semesterSelect");
        const selectedSemester = semesterSelectEl ? semesterSelectEl.value : "";
        const isAllSem = !selectedSemester || selectedSemester.toLowerCase() === "all";
        const filteredResults = studentData.results ? (isAllSem ? studentData.results : studentData.results.filter(function(result){
            return getSemesterNumber(result.semester) === getSemesterNumber(selectedSemester);
        })) : [];
        displayResults(filteredResults);
    }
    catch (e) {
        // If admin, show admin state not error
        if(isAdminLike){ showAdminState(authUser||userId); return; }
        showError(e.message)
    }
}
function displayResults(items) {
    const t = document.getElementById('resultTable');
    if (!t) return;
    if (!items.length) {
        t.innerHTML = '<tr><td colspan="8" class="empty">No subject result data available yet.</td></tr>';
        return
    }
    t.innerHTML = items.map(x => `<tr><td>${x.code ?? '--'}</td><td>${x.subject ?? '--'}</td><td>${x.credits ?? '--'}</td><td>${x.marks ?? '--'}</td><td>${x.re ?? '--'}</td><td>${x.gradePoint ?? x.gp ?? '--'}</td><td>${x.grade ?? '--'}</td><td class="${x.status === 'PASS' ? 'status-pass' : 'status-fail'}">${x.status ?? '--'}</td></tr>`).join('')
}

function getSemesterNumber(value){
    if(value == null) return '';
    const text = String(value).trim().toLowerCase();
    if(text.includes('1st') || text === '1') return '1';
    if(text.includes('2nd') || text === '2') return '2';
    if(text.includes('3rd') || text === '3') return '3';
    if(text.includes('4th') || text === '4') return '4';
    if(text.includes('5th') || text === '5') return '5';
    if(text.includes('6th') || text === '6') return '6';
    if(text.includes('7th') || text === '7') return '7';
    if(text.includes('8th') || text === '8') return '8';
    const m = text.match(/[1-8]/);
    return m ? m[0] : '';
}

const semesterSelect = document.getElementById('semesterSelect');
if(semesterSelect){
    semesterSelect.addEventListener("change" ,function(){
    if (!studentData || !studentData.results) return;
    const selectedSemester = this.value;
    if (!selectedSemester || selectedSemester.toLowerCase() === "all") {
        displayResults(studentData.results);
        return;
    }
    const selectedNumber = getSemesterNumber(selectedSemester);
    const filteredResults = studentData.results.filter(function(result){
            return getSemesterNumber(result.semester) === selectedNumber;
        }
    );
    displayResults(filteredResults)
});
}

document.addEventListener('DOMContentLoaded', async () => {
    const role = await getRole();
    if(!role){
        // Unauthenticated: redirect to login per existing auth guard
        window.location.href = 'index.html';
        return;
    }
    // Role-first initialization: do not call loadStudent for HOD/ADMIN
    if(role==='HOD'){
        document.getElementById('dashboardTitle').textContent='HOD Dashboard';
        document.getElementById('dashboardSubtitle').textContent='Department Result Analysis';
        document.getElementById('hodDashboardHeader').classList.remove('hidden');
        document.getElementById('hodDeptOverview').classList.remove('hidden');
        document.getElementById('hodPerformanceOverview').classList.remove('hidden');
        document.getElementById('hodQuickAccess').classList.remove('hidden');
        // Hide student-specific UI for HOD
        const sdc=document.getElementById('studentDetailsCard');
        if(sdc) sdc.classList.add('hidden');
        const ssg=document.getElementById('studentSummaryGrid');
        if(ssg) ssg.classList.add('hidden');
        const rs=document.getElementById('resultSection');
        if(rs) rs.classList.add('hidden');
        // HOD: load department data, not student
        loadHodDashboard();
        loadUploadHistory();
        await updateUploadVisibility();
        // Do NOT call loadStudent() for HOD
    } else if(role==='STUDENT'){
        document.getElementById('dashboardTitle').textContent='Student Dashboard';
        document.getElementById('dashboardSubtitle').textContent='Your academic performance at a glance.';
        document.getElementById('studentDetailsCard').classList.remove('hidden');
        document.getElementById('studentSummaryGrid').classList.remove('hidden');
        document.getElementById('resultSection').classList.remove('hidden');
        // Hide HOD
        document.getElementById('hodDashboardHeader').classList.add('hidden');
        document.getElementById('hodDeptOverview').classList.add('hidden');
        document.getElementById('hodPerformanceOverview').classList.add('hidden');
        document.getElementById('hodQuickAccess').classList.add('hidden');
        await updateUploadVisibility();
        loadStudent();
        loadUploadHistory();
    } else {
        // ADMIN
        document.getElementById('dashboardTitle').textContent='Dashboard';
        document.getElementById('dashboardSubtitle').textContent='Academic overview';
        const sdc=document.getElementById('studentDetailsCard');
        if(sdc) sdc.classList.add('hidden');
        const ssg=document.getElementById('studentSummaryGrid');
        if(ssg) ssg.classList.add('hidden');
        const rs=document.getElementById('resultSection');
        if(rs) rs.classList.add('hidden');
        document.getElementById('hodDashboardHeader').classList.add('hidden');
        document.getElementById('hodDeptOverview').classList.add('hidden');
        document.getElementById('hodPerformanceOverview').classList.add('hidden');
        document.getElementById('hodQuickAccess').classList.add('hidden');
        await updateUploadVisibility();
        // Show Admin Account via loadStudent's showAdminState (will be called but we skip loadStudent for ADMIN)
        // Instead, directly show admin state without fetching student
        showAdminState(await (await fetch(`${API_BASE}/api/auth/me`, {credentials:'include'}).then(r=>r.json()).catch(()=>({}))).authenticatedUser || 'Admin');
        loadUploadHistory();
    }
    // Sidebar navigation visibility: "My Result" only for STUDENT
    const resBtn = document.getElementById('resultBtn');
    if(resBtn){
        if(role==='STUDENT'){
            resBtn.classList.remove('hidden');
        } else {
            resBtn.classList.add('hidden');
        }
    }
    // Mobile sidebar toggle
    const hamburger = document.getElementById('hamburgerBtn');
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');
    if(hamburger && sidebar){
        hamburger.addEventListener('click', ()=>{
            sidebar.classList.toggle('open');
            if(overlay) overlay.classList.toggle('show', sidebar.classList.contains('open'));
        });
    }
    if(overlay && sidebar){
        overlay.addEventListener('click', ()=>{
            sidebar.classList.remove('open');
            overlay.classList.remove('show');
        });
    }
    // Close sidebar on nav click (mobile)
    document.querySelectorAll('.menu button').forEach(btn=>{
        btn.addEventListener('click', ()=>{
            if(window.innerWidth<=850 && sidebar){
                sidebar.classList.remove('open');
                if(overlay) overlay.classList.remove('show');
            }
        });
    });
});

async function getRole(){
    try{
        const r=await fetch(`${API_BASE}/api/auth/me`, {credentials:'include'});
        if(!r.ok) return null;
        const d=await r.json();
        return (d.role||'').toUpperCase();
    }catch(e){ return null; }
}
async function loadHodDashboard(){
    try{
        const r=await fetch(`${API_BASE}/students`, {credentials:'include'});
        if(!r.ok) throw new Error('Failed');
        const students=await r.json();
        // Calculate overview: Total, Passed, Failed, Avg SGPA, Avg %, Backlogs
        const total=students.length;
        let passed=0, failed=0, sgpaSum=0, sgpaCnt=0, percSum=0, percCnt=0, backlogSum=0;
        students.forEach(s=>{
            const res=(s.result||'').toUpperCase();
            if(res==='PASS') passed++; else if(res==='FAIL') failed++;
            if(typeof s.sgpa==='number'){ sgpaSum+=s.sgpa; sgpaCnt++; }
            else if(s.sgpa!=null && !isNaN(parseFloat(s.sgpa))){ sgpaSum+=parseFloat(s.sgpa); sgpaCnt++; }
            if(typeof s.percentage==='number'){ percSum+=s.percentage; percCnt++; }
            else if(s.percentage!=null && !isNaN(parseFloat(s.percentage))){ percSum+=parseFloat(s.percentage); percCnt++; }
            backlogSum+= (s.backlog||0);
        });
        const avgSgpa = sgpaCnt? (sgpaSum/sgpaCnt).toFixed(2) : '--';
        const avgPerc = percCnt? (percSum/percCnt).toFixed(1)+'%' : '--';
        const setIf=(id,v)=>{ const e=document.getElementById(id); if(e) e.textContent=v; };
        setIf('hodTotalStudents', total);
        setIf('hodPassed', passed);
        setIf('hodFailed', failed);
        setIf('hodAvgSgpa', avgSgpa);
        setIf('hodAvgPerc', avgPerc);
        setIf('hodTotalBacklogs', backlogSum);
    }catch(e){
        console.error('HOD overview failed',e);
    }
    // Load subject stats for performance overview
    try{
        const r=await fetch(`${API_BASE}/students/analytics/subject-stats`, {credentials:'include'});
        if(!r.ok) throw new Error('Failed');
        const data=await r.json();
        const tbody=document.getElementById('hodSubjectStatsBody');
        if(tbody){
            if(!data.length) tbody.innerHTML='<tr><td colspan="4" class="empty">No subject data</td></tr>';
            else tbody.innerHTML=data.slice(0,8).map(d=>`<tr><td>${escapeHtml(d.subject)}</td><td>${escapeHtml(d.code)}</td><td>${d.averageMarks!=null?d.averageMarks:'--'}</td><td>${d.passPercentage!=null?d.passPercentage+'%':'--'}</td></tr>`).join('');
        }
    }catch(e){
        const tbody=document.getElementById('hodSubjectStatsBody');
        if(tbody) tbody.innerHTML='<tr><td colspan="4" class="empty">Unable to load</td></tr>';
    }
}

async function updateUploadVisibility(){
    const card = document.getElementById('uploadCard');
    if(!card) return;
    try{
        const r = await fetch(`${API_BASE}/api/auth/me`, { credentials: 'include' });
        if(!r.ok){ card.classList.add('hidden'); return; }
        const d = await r.json();
        const role = (d.role||'').toUpperCase();
        if(role==='HOD'){
            card.classList.remove('hidden');
        } else {
            card.classList.add('hidden');
        }
        // Handle mustChangePassword for STUDENT
        if(d.mustChangePassword && role==='STUDENT'){
            const cpCard = document.getElementById('changePasswordCard');
            if(cpCard) cpCard.classList.remove('hidden');
            const msg = document.getElementById('mustChangeMsg');
            if(msg) msg.textContent = 'For security, please change your initial password (currently your mobile number).';
        }
    }catch(e){
        card.classList.add('hidden');
    }
}
// Change password handlers
const changePwdBtn = document.getElementById('changePwdBtn');
const cancelChangePwdBtn = document.getElementById('cancelChangePwdBtn');
const doChangePwdBtn = document.getElementById('doChangePwdBtn');
if(changePwdBtn) changePwdBtn.addEventListener('click', ()=>{ const c=document.getElementById('changePasswordCard'); if(c) c.classList.remove('hidden'); c.scrollIntoView({behavior:'smooth'}); });
if(cancelChangePwdBtn) cancelChangePwdBtn.addEventListener('click', ()=>{ const c=document.getElementById('changePasswordCard'); if(c) c.classList.add('hidden'); });
if(doChangePwdBtn) doChangePwdBtn.addEventListener('click', async ()=>{
    const cur=document.getElementById('currentPwd')?.value||'';
    const np=document.getElementById('newPwd')?.value||'';
    const cp=document.getElementById('confirmPwd')?.value||'';
    const msgEl=document.getElementById('changePwdMsg');
    if(msgEl) msgEl.textContent='';
    if(!cur||!np||!cp){ if(msgEl){ msgEl.textContent='All fields required'; msgEl.style.color='#dc2626'; } return; }
    if(np!==cp){ if(msgEl){ msgEl.textContent='New passwords do not match'; msgEl.style.color='#dc2626'; } return; }
    try{
        const r=await fetch(`${API_BASE}/api/auth/change-password`, { method:'POST', headers:{'Content-Type':'application/json'}, credentials:'include', body:JSON.stringify({currentPassword:cur,newPassword:np,confirmPassword:cp}) });
        const d=await r.json();
        if(!r.ok) throw new Error(d.message||'Failed');
        if(msgEl){ msgEl.textContent=d.message||'Password changed'; msgEl.style.color='#15803d'; }
        setTimeout(()=>{ const c=document.getElementById('changePasswordCard'); if(c) c.classList.add('hidden'); },1000);
        // Clear mustChangePassword flag
        localStorage.removeItem('mustChangePassword');
    }catch(e){
        if(msgEl){ msgEl.textContent=e.message||'Failed'; msgEl.style.color='#dc2626'; }
    }
});

async function loadUploadHistory(){
    const box=document.getElementById('uploadHistoryBox');
    if(!box) return;
    try{
        const r=await fetch(`${API_BASE}/api/import/history/latest`, { credentials: 'include' });
        if(!r.ok) throw new Error('no history');
        const data=await r.json();
        if(data.message && data.message.includes('No Excel')){
            box.innerHTML='No Excel sheet has been uploaded yet.';
            box.className='alert alert-warn';
            box.classList.remove('hidden');
        } else if(data.uploadedBy || data.uploadedAt){
            const dt = data.uploadedAt ? new Date(data.uploadedAt) : null;
            const dateTimeStr = dt ? dt.toLocaleString('en-GB', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit', hour12:true }) : '--';
            // Show ONLY username and upload date/time per spec
            box.innerHTML=`Uploaded by: ${escapeHtml(data.uploadedBy||'unknown')}<br>Uploaded on: ${escapeHtml(dateTimeStr)}`;
            box.className='alert alert-success';
            box.classList.remove('hidden');
        } else if(data.fileName){
            // Fallback for legacy records lacking uploadedAt but having fileName
            const dt = data.uploadedAt ? new Date(data.uploadedAt) : null;
            const dateTimeStr = dt ? dt.toLocaleString('en-GB', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit', hour12:true }) : '--';
            box.innerHTML=`Uploaded by: ${escapeHtml(data.uploadedBy||'unknown')}<br>Uploaded on: ${escapeHtml(dateTimeStr)}`;
            box.className='alert alert-success';
            box.classList.remove('hidden');
        } else {
            box.classList.add('hidden');
        }
    }catch(e){
        box.classList.add('hidden');
    }
}
const uploadInfoBtn=document.getElementById('uploadInfoBtn');
if(uploadInfoBtn) uploadInfoBtn.addEventListener('click', ()=>{
    const box=document.getElementById('uploadHistoryBox');
    if(!box) return;
    if(box.classList.contains('hidden')){
        loadUploadHistory();
        box.scrollIntoView({behavior:'smooth'});
    } else {
        box.classList.add('hidden');
    }
});

const dashBtn = document.getElementById('dashboardBtn');
if(dashBtn) dashBtn.addEventListener('click', () => scrollTo({ top: 0, behavior: 'smooth' }));
const resBtn = document.getElementById('resultBtn');
if(resBtn) resBtn.addEventListener('click', () => document.getElementById('resultSection').scrollIntoView({ behavior: 'smooth' }));
const rankBtn = document.getElementById('rankBtn');
if(rankBtn) rankBtn.addEventListener('click', () => location.href = 'rank_analysis.html');
const profileBtn = document.getElementById('profileBtn');
if(profileBtn) profileBtn.addEventListener('click', () => {
    const phoneEl = document.getElementById('profilePhone');
    const phone = phoneEl ? phoneEl.textContent : '--';
    const usnVal = document.getElementById('studentUSN') ? document.getElementById('studentUSN').textContent : usn;
    const emailVal = document.getElementById('profileEmail') ? document.getElementById('profileEmail').textContent : '--';
    const nameVal = document.getElementById('studentName') ? document.getElementById('studentName').textContent : '--';
    alert(`Name: ${nameVal}\nUSN: ${usnVal}\nEmail: ${emailVal}\nPhone: ${phone}`);
});
const logoutBtn = document.getElementById('logoutBtn');
if(logoutBtn) logoutBtn.addEventListener('click', async () => {
    try { await fetch(`${API_BASE}/api/auth/logout`, { method: 'POST', credentials: 'include' }); } catch(e){}
    localStorage.clear(); location.href = 'index.html';
});

// ===== Upload / Update Student Results — Dashboard workflow =====
let selectedFile = null;
let lastPreview = null;
const branchSel = document.getElementById('branchSelect');
const batchSel = document.getElementById('batchSelect');
const semUploadSel = document.getElementById('semesterSelectUpload');
const fileInput = document.getElementById('excelFile');
const browseBtn = document.getElementById('browseBtn');
const dropZone = document.getElementById('dropZone');
const fileInfo = document.getElementById('fileInfo');
const fileNameEl = document.getElementById('fileName');
const fileSizeEl = document.getElementById('fileSize');
const fileError = document.getElementById('fileError');
const clearFileBtn = document.getElementById('clearFile');
const previewBtn = document.getElementById('previewBtn');
const validationMsg = document.getElementById('validationMsg');

function show(el, yes){ if(!el) return; if(yes) el.classList.remove('hidden'); else el.classList.add('hidden'); }
function escapeHtml(s){ if(s==null) return ''; return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }

if (browseBtn && fileInput) browseBtn.addEventListener('click', () => fileInput.click());
if (clearFileBtn) clearFileBtn.addEventListener('click', clearFile);
if (fileInput) fileInput.addEventListener('change', e => handleFile(e.target.files[0]));
if (dropZone){
    ['dragenter','dragover'].forEach(ev => dropZone.addEventListener(ev, e=>{e.preventDefault(); e.stopPropagation(); dropZone.classList.add('dragover');}));
    ['dragleave','drop'].forEach(ev => dropZone.addEventListener(ev, e=>{e.preventDefault(); e.stopPropagation(); dropZone.classList.remove('dragover');}));
    dropZone.addEventListener('drop', e=>{ const f=e.dataTransfer.files[0]; handleFile(f); });
}
function handleFile(file){
    hidePreview(); hideConfirm(); hideSuccess(); hideImportError();
    if(fileError) { fileError.classList.add('hidden'); fileError.textContent=''; }
    if(!file) return;
    const lower = (file.name||'').toLowerCase();
    if(!lower.endsWith('.xlsx') && !lower.endsWith('.xls')){
        showFileError('Invalid file type. Please select .xlsx (or .xls) only.');
        selectedFile=null; updateChecklist(); return;
    }
    if(file.size > 20*1024*1024){ showFileError('File too large (max 20 MB).'); selectedFile=null; updateChecklist(); return; }
    selectedFile=file;
    if(fileNameEl) fileNameEl.textContent=file.name;
    if(fileSizeEl) fileSizeEl.textContent=`— ${(file.size/1024).toFixed(1)} KB`;
    if(fileInfo) fileInfo.classList.remove('hidden');
    updateChecklist();
}
function clearFile(){
    selectedFile=null; if(fileInput) fileInput.value=''; if(fileInfo) fileInfo.classList.add('hidden'); if(fileNameEl) fileNameEl.textContent='—'; hidePreview(); hideConfirm(); updateChecklist();
}
function showFileError(msg){ if(!fileError) return; fileError.textContent=msg; fileError.classList.remove('hidden'); }

[branchSel, batchSel, semUploadSel].forEach(el=>{
    if(!el) return; el.addEventListener('change', updateChecklist); el.addEventListener('input', updateChecklist);
});
function updateChecklist(){
    const b = branchSel ? branchSel.value.trim() : '';
    const batch = batchSel ? batchSel.value.trim() : '';
    const sem = semUploadSel ? semUploadSel.value.trim() : '';
    const fileOk = !!selectedFile;
    const allOk = !!b && !!batch && !!sem && fileOk;
    if(previewBtn) previewBtn.disabled=!allOk;
    // Checklist and validationMsg removed per spec #4 — required fields use native * labels, button disabled until valid
}
updateChecklist();

if(previewBtn) previewBtn.addEventListener('click', doPreview);
async function doPreview(){
    hidePreview(); hideConfirm(); hideSuccess(); hideImportError();
    const branch = branchSel.value.trim();
    const batch = batchSel.value.trim();
    const semester = semUploadSel.value.trim();
    if(!branch || !batch || !semester || !selectedFile){
        // Button is disabled until valid, but guard anyway without showing old checklist warning
        return;
    }
    const previewCard=document.getElementById('previewCard');
    const previewStatus=document.getElementById('previewStatus');
    const previewMeta=document.getElementById('previewMeta');
    const previewUploaderInfo=document.getElementById('previewUploaderInfo');
    show(previewCard, true); show(previewStatus, true);
    previewStatus.textContent='Submitting Excel and generating preview — no data will be written yet...';
    previewStatus.className='alert alert-warn';
    show(previewMeta, false);
    if(previewUploaderInfo) previewUploaderInfo.classList.add('hidden');
    previewBtn.disabled=true; previewBtn.textContent='⏳ Submitting Excel...';
    try{
        const uploadedBy = localStorage.getItem('userId') || localStorage.getItem('usn') || 'unknown';
        const fd=new FormData();
        fd.append('file', selectedFile);
        fd.append('branch', branch);
        fd.append('department', branch);
        fd.append('semester', semester);
        fd.append('batch', batch);
        fd.append('uploadedBy', uploadedBy);
        const resp=await fetch(`${API_BASE}/api/import/preview`, {method:'POST', body:fd, credentials: 'include'});
        const data=await resp.json();
        if(!resp.ok) throw new Error(data.error || data.message || 'Preview failed');
        lastPreview=data; renderPreview(data);
    }catch(e){
        previewStatus.textContent='Preview failed: '+(e.message||e)+'. Ensure backend is running.';
        previewStatus.className='alert alert-error'; console.error(e);
    }finally{
        previewBtn.disabled=false; previewBtn.textContent='📤 Submit Excel'; updateChecklist();
        if(lastPreview) document.getElementById('previewCard').scrollIntoView({behavior:'smooth'});
    }
}
function renderPreview(d){
    const previewStatus=document.getElementById('previewStatus');
    const previewMeta=document.getElementById('previewMeta');
    const previewUploaderInfo=document.getElementById('previewUploaderInfo');
    // Show ONLY backend-authenticated username + backend-generated timestamp per spec (do NOT trust browser)
    if(previewUploaderInfo){
        const uploader = d.uploadedBy || 'unknown';
        const dt = d.uploadedAt ? new Date(d.uploadedAt) : new Date();
        const dateTimeStr = dt.toLocaleString('en-GB', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit', hour12:true });
        previewUploaderInfo.innerHTML=`Uploaded by: ${escapeHtml(uploader)}<br>Uploaded on: ${escapeHtml(dateTimeStr)}`;
        previewUploaderInfo.classList.remove('hidden');
    }
    document.getElementById('p-file').textContent=d.fileName || (selectedFile?selectedFile.name:'—');
    document.getElementById('p-branch').textContent=d.branch||d.department||'—';
    document.getElementById('p-sem').textContent=d.semester||'—';
    document.getElementById('p-batch').textContent=d.batch||'—';
    document.getElementById('p-students').textContent=d.studentsDetected??0;
    document.getElementById('p-subjects').textContent=d.subjectsDetected??0;
    document.getElementById('p-rows').textContent=d.totalRows??0;
    // Sync preview (18.7) — New/Existing/WithChanges/AlreadyUpToDate etc.
    const syncBox=document.getElementById('syncPreviewBox');
    const syncGrid=document.getElementById('syncPreviewGrid');
    if(syncBox && syncGrid){
        const ns=d.newStudents??0, es=d.existingStudents??0, swc=d.studentsWithChanges??0, sau=d.studentsAlreadyUpToDate??0;
        const nsr=d.newSubjectResults??0, stu=d.subjectResultsToUpdate??0, sau2=d.subjectResultsAlreadyUpToDate??0;
        const ir=d.invalidRows??0, dr=d.duplicateRowsWithinFile??0;
        // Update grid numbers
        const setIf=(id,v)=>{ const e=document.getElementById(id); if(e) e.textContent=v; };
        setIf('p-newStudents', ns); setIf('p-existingStudents', es); setIf('p-studentsWithChanges', swc); setIf('p-studentsUnchanged', sau);
        setIf('p-newSubjects', nsr); setIf('p-subjectToUpdate', stu); setIf('p-subjectUnchanged', sau2);
        setIf('p-invalidRows', ir); setIf('p-duplicateRows', dr);
        // Build summary text
        let summary='';
        if(ns===0 && swc===0 && nsr===0 && stu===0 && es>0){
            summary='<strong>Already uploaded — no changes detected.</strong><br>Existing Students: '+es+' already up to date. No new or changed subject results.';
        } else {
            summary='<strong>Preview Summary</strong><br>'+
                'New Students: '+ns+' | Existing Students: '+es+' | With Changes: '+swc+' | Already Up To Date: '+sau+'<br>'+
                'New Subject Results: '+nsr+' | To Update: '+stu+' | Already Up To Date: '+sau2+'<br>'+
                'Invalid Rows: '+ir+' | Duplicate Rows Within File: '+dr;
        }
        syncBox.innerHTML=summary;
        syncBox.classList.remove('hidden');
        syncGrid.classList.remove('hidden');
    }
    const errorBox=document.getElementById('errorBox');
    const warnBox=document.getElementById('warnBox');
    if(errorBox){
        if(d.validationErrors && d.validationErrors.length){
            errorBox.innerHTML='<strong>❌ Validation errors:</strong><ul style="margin:6px 0 0; padding-left:18px;">'+d.validationErrors.map(e=>`<li>${escapeHtml(e)}</li>`).join('')+'</ul>';
            errorBox.className='alert alert-error'; errorBox.classList.remove('hidden');
        } else { errorBox.classList.add('hidden'); errorBox.innerHTML=''; }
    }
    if(warnBox){
        if(d.validationWarnings && d.validationWarnings.length){
            warnBox.innerHTML='<strong>⚠️ Warnings:</strong><ul style="margin:6px 0 0; padding-left:18px;">'+d.validationWarnings.map(w=>`<li>${escapeHtml(w)}</li>`).join('')+'</ul>';
            warnBox.className='alert alert-warn'; warnBox.classList.remove('hidden');
        } else { warnBox.classList.add('hidden'); warnBox.innerHTML=''; }
    }
    const headEl=document.getElementById('previewHead');
    const bodyEl=document.getElementById('previewBody');
    if(headEl && bodyEl){
        const headers=d.headers||[];
        if(headers.length) headEl.innerHTML='<tr>'+headers.map(h=>`<th>${escapeHtml(h)}</th>`).join('')+'</tr>';
        else headEl.innerHTML='<tr><th>No headers</th></tr>';
        const rows=d.previewRows||[];
        if(rows.length) bodyEl.innerHTML=rows.map(row=>'<tr>'+headers.map(h=>`<td title="${escapeHtml(String(row[h]||''))}">${escapeHtml(String(row[h]||''))}</td>`).join('')+'</tr>').join('');
        else bodyEl.innerHTML=`<tr><td colspan="${headers.length||1}" class="empty">No rows to preview</td></tr>`;
    }
    if(d.hasErrors){
        previewStatus.textContent='❌ Preview finished with errors — please fix before confirming.';
        previewStatus.className='alert alert-error';
    } else if(d.validationWarnings && d.validationWarnings.length){
        previewStatus.textContent='⚠️ Preview finished with warnings — you may still continue, invalid rows will be skipped.';
        previewStatus.className='alert alert-warn';
    } else {
        previewStatus.textContent='✅ Preview successful — review and continue to confirmation. No records were written.';
        previewStatus.className='alert alert-success';
    }
    show(previewStatus, true); show(previewMeta, true);
    const confirmBtn=document.getElementById('confirmBtn');
    if(confirmBtn){
        if((d.studentsDetected??0)===0 || d.hasErrors){ confirmBtn.disabled=true; confirmBtn.style.opacity='0.5'; }
        else { confirmBtn.disabled=false; confirmBtn.style.opacity='1'; }
    }
}
function hidePreview(){ const c=document.getElementById('previewCard'); if(c) c.classList.add('hidden'); const ui=document.getElementById('previewUploaderInfo'); if(ui) ui.classList.add('hidden'); lastPreview=null; }
function hideConfirm(){ const c=document.getElementById('confirmCard'); if(c) c.classList.add('hidden'); }
function hideSuccess(){ const c=document.getElementById('successCard'); if(c) c.classList.add('hidden'); }
function hideImportError(){ const e=document.getElementById('importError'); if(e){ e.classList.add('hidden'); e.textContent=''; } }

const confirmBtn=document.getElementById('confirmBtn');
const editBtn=document.getElementById('editBtn');
if(editBtn) editBtn.addEventListener('click', ()=> document.getElementById('uploadCard').scrollIntoView({behavior:'smooth'}));
if(confirmBtn) confirmBtn.addEventListener('click', showConfirm);
function showConfirm(){
    if(!lastPreview) return;
    const card=document.getElementById('confirmCard');
    const summary=document.getElementById('confirmSummary');
    if(summary) summary.innerHTML=`Department: <strong>${escapeHtml(lastPreview.branch||lastPreview.department||'—')}</strong><br>Batch: <strong>${escapeHtml(lastPreview.batch||'—')}</strong><br>Semester: <strong>${escapeHtml(lastPreview.semester||'—')}</strong><br>Students: <strong>${lastPreview.studentsDetected??0}</strong> (from ${lastPreview.totalRows??0} rows)<br>Subjects: <strong>${lastPreview.subjectsDetected??0}</strong><br>File: <strong>${escapeHtml(lastPreview.fileName||'')}</strong>`;
    show(card, true); card.scrollIntoView({behavior:'smooth'}); hideImportError();
}
const cancelImportBtn=document.getElementById('cancelImport');
if(cancelImportBtn) cancelImportBtn.addEventListener('click', ()=> document.getElementById('confirmCard').classList.add('hidden'));
const doImportBtn=document.getElementById('doImportBtn');
if(doImportBtn) doImportBtn.addEventListener('click', doImport);
async function doImport(){
    if(!selectedFile || !lastPreview){ showImportError('No file or preview available.'); return; }
    const branch=branchSel.value.trim(); const batch=batchSel.value.trim(); const semester=semUploadSel.value.trim();
    doImportBtn.disabled=true; doImportBtn.textContent='⏳ Importing...';
    const errEl=document.getElementById('confirmError'); if(errEl){ errEl.classList.add('hidden'); errEl.textContent=''; }
    try{
        const uploadedBy = localStorage.getItem('userId') || localStorage.getItem('usn') || 'unknown';
        const fd=new FormData(); fd.append('file', selectedFile); fd.append('branch', branch); fd.append('department', branch); fd.append('semester', semester); fd.append('batch', batch); fd.append('uploadedBy', uploadedBy);
        const resp=await fetch(`${API_BASE}/api/import/confirm`, {method:'POST', body:fd, credentials: 'include'});
        const data=await resp.json().catch(()=>({}));
        if(resp.status===401){
            const msg=data.message||'Not authenticated. Please login as HOD before Confirm Import.';
            if(errEl){ errEl.innerHTML=`<strong>❌ Not authenticated:</strong> ${escapeHtml(msg)}`; errEl.classList.remove('hidden'); }
            showImportError('❌ '+msg);
            return;
        }
        if(!resp.ok) throw new Error(data.message || data.error || 'Import failed');
        if(!data.success){
            if(errEl){ const list=(data.errors||[]).map(e=>`<li>${escapeHtml(e)}</li>`).join(''); errEl.innerHTML=`<strong>❌ Import not completed:</strong> ${escapeHtml(data.message||'')}`+(list?`<ul style="margin:6px 0 0; padding-left:18px;">${list}</ul>`:''); errEl.classList.remove('hidden'); }
            return;
        }
        showSuccess(data); hideConfirm();
        // Refresh history to show new authenticated uploader immediately
        loadUploadHistory();
    }catch(e){
        showImportError('❌ Import failed. No records were added: '+(e.message||e)); console.error(e);
        if(errEl){ errEl.textContent='Import failed: '+(e.message||e); errEl.classList.remove('hidden'); }
    }finally{ doImportBtn.disabled=false; doImportBtn.textContent='✅ Confirm Import'; }
}
function showSuccess(data){
    const card=document.getElementById('successCard');
    const summary=document.getElementById('successSummary');
    const sBranch=document.getElementById('s-branch'); const sSem=document.getElementById('s-sem'); const sBatch=document.getElementById('s-batch');
    const warnEl=document.getElementById('successWarnings');
    const syncGrid=document.getElementById('successSyncGrid');
    const syncBox=document.getElementById('successSyncBox');
    // Detailed sync summary (18.8)
    const sn=data.studentsNew??0, su=data.studentsUpdated??0, sun=data.studentsUnchanged??0;
    const si=data.subjectResultsInserted??0, siu=data.subjectResultsUpdated??0, siun=data.subjectResultsUnchanged??0;
    const sk=data.skippedRows??0, dup=data.duplicateRowsWithinFile??0;
    // Fallback to old fields if new not present
    const totalImp = data.studentsImported ?? (sn+su);
    const subImp = data.subjectResultsImported ?? (si+siu);
    let msgHtml='';
    if(data.message && data.message.includes('Already uploaded')){
        msgHtml=`<strong>${escapeHtml(data.message)}</strong>`;
    } else if(sn===0 && su===0 && sun>0){
        msgHtml='<strong>Already uploaded — no changes detected.</strong><br>Students already up to date: '+sun;
    } else {
        msgHtml=`Import completed successfully.<br>Students — New: <strong>${sn}</strong> | Updated: <strong>${su}</strong> | Unchanged: <strong>${sun}</strong><br>Subject Results — Inserted: <strong>${si}</strong> | Updated: <strong>${siu}</strong> | Unchanged: <strong>${siun}</strong>`;
        if(sk>0 || dup>0) msgHtml+=`<br>Skipped/Invalid Rows: <strong>${sk}</strong> | Duplicate Rows: <strong>${dup}</strong>`;
    }
    if(summary) summary.innerHTML=msgHtml + (data.message && !data.message.includes('Already uploaded') && !msgHtml.includes(data.message) ? `<br><span class="muted">${escapeHtml(data.message)}</span>` : '');
    if(sBranch) sBranch.textContent=data.branch||'—'; if(sSem) sSem.textContent=data.semester||'—'; if(sBatch) sBatch.textContent=data.batch||'—';
    if(syncGrid){
        const setIf=(id,v)=>{ const e=document.getElementById(id); if(e) e.textContent=v; };
        setIf('s-new', sn); setIf('s-updated', su); setIf('s-unchanged', sun);
        setIf('s-subInserted', si); setIf('s-subUpdated', siu); setIf('s-subUnchanged', siun);
        setIf('s-skipped', sk); setIf('s-dupRows', dup);
        syncGrid.classList.remove('hidden');
    }
    if(syncBox){
        if(sn===0 && su===0 && sun>0) syncBox.innerHTML='No duplicate SubjectResult records were created. All records already up to date.';
        else if(si>0 || siu>0) syncBox.innerHTML='No duplicate SubjectResult records were created.';
        else syncBox.innerHTML='';
        if(syncBox.innerHTML) syncBox.classList.remove('hidden'); else syncBox.classList.add('hidden');
    }
    if(warnEl){
        if(data.errors && data.errors.length){ warnEl.innerHTML='<strong>⚠️ Some rows were skipped:</strong><ul style="margin:6px 0 0; padding-left:18px;">'+data.errors.map(e=>`<li>${escapeHtml(e)}</li>`).join('')+'</ul>'; warnEl.classList.remove('hidden'); } else warnEl.classList.add('hidden');
    }
    show(card, true); card.scrollIntoView({behavior:'smooth'});
}
function showImportError(msg){ const el=document.getElementById('importError'); if(!el) return; el.textContent=msg; el.classList.remove('hidden'); el.scrollIntoView({behavior:'smooth'}); }
const importAnotherBtn=document.getElementById('importAnotherBtn');
if(importAnotherBtn) importAnotherBtn.addEventListener('click', ()=>{
    clearFile(); hidePreview(); hideConfirm(); hideSuccess(); hideImportError();
    if(branchSel) branchSel.value=''; if(batchSel) batchSel.value=''; if(semUploadSel) semUploadSel.value='';
    updateChecklist(); window.scrollTo({top:0, behavior:'smooth'});
});
const refreshBtn=document.getElementById('refreshAfterImport');
if(refreshBtn) refreshBtn.addEventListener('click', ()=>{ loadStudent(); document.getElementById('resultSection').scrollIntoView({behavior:'smooth'}); });
