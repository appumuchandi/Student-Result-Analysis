const API_BASE = window.APP_CONFIG?.API_BASE_URL || "http://localhost:8081";

const usn = localStorage.getItem('usn');
const collegeCode = localStorage.getItem('collegeCode');
const branch = localStorage.getItem('branch');
let studentData = null;
const $ = (id) => document.getElementById(id);

async function loadStudent() {
    if (!usn || !collegeCode || !branch) {
        showError('Student details not found. Go back to Home.');
        return
    }
    try {
        const r = await fetch(`${API_BASE}/students/usn/${encodeURIComponent(usn)}?collegeCode=${encodeURIComponent(collegeCode)}&branch=${encodeURIComponent(branch)}`);
        if (!r.ok) throw new Error('Student details not found');
        const d = await r.json();
        console.log("Student data" ,d);
        studentData = d;
        set('studentName', d.name);
        set('studentUSN', d.usn);
        set('branch', d.branch);
        set('semester', d.semester);
        set('year', d.academicYear);
        set('profileEmail', d.email);
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
        showError(e.message)
    }
}
function set(id, v) {
    const el = document.getElementById(id);
    if (el) el.textContent = v ?? '--';
}
function showError(m) {
    set('studentName', 'Data unavailable');
    const t = document.getElementById('resultTable');
    if (t) t.innerHTML = `<tr><td colspan="8" class="empty">${m}</td></tr>`
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

document.addEventListener('DOMContentLoaded', () => {
    loadStudent();
    loadUploadHistory();
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
if(profileBtn) profileBtn.addEventListener('click', () => alert(`Name: ${document.getElementById('studentName').textContent}\nUSN: ${usn}\nEmail: ${document.getElementById('profileEmail').textContent}`));
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
    previewStatus.textContent='Submitting Excel and generating preview — no data will be written to MySQL yet...';
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
        const data=await resp.json();
        if(!resp.ok) throw new Error(data.message || data.error || 'Import failed');
        if(!data.success){
            if(errEl){ const list=(data.errors||[]).map(e=>`<li>${escapeHtml(e)}</li>`).join(''); errEl.innerHTML=`<strong>❌ Import not completed:</strong> ${escapeHtml(data.message||'')}`+(list?`<ul style="margin:6px 0 0; padding-left:18px;">${list}</ul>`:''); errEl.classList.remove('hidden'); }
            return;
        }
        showSuccess(data); hideConfirm();
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
    if(summary) summary.innerHTML=`Students imported: <strong>${data.studentsImported??0}</strong><br>Subject results imported: <strong>${data.subjectResultsImported??0}</strong><br><span class="muted">${escapeHtml(data.message||'')}</span>`;
    if(sBranch) sBranch.textContent=data.branch||'—'; if(sSem) sSem.textContent=data.semester||'—'; if(sBatch) sBatch.textContent=data.batch||'—';
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
