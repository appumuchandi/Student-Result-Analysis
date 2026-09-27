const API_BASE = window.APP_CONFIG?.API_BASE_URL || "http://localhost:8081";

let selectedFile = null;
let lastPreview = null;

// DOM helpers
const $ = id => document.getElementById(id);
const show = (el, yes) => {
    if (!el) return;
    if (yes) el.classList.remove('hidden');
    else el.classList.add('hidden');
};

// HOD-only check: hide upload if not HOD
(async()=>{
    try{
        const r=await fetch(`${API_BASE}/api/auth/me`, {credentials:'include'});
        if(!r.ok) throw new Error('no auth');
        const d=await r.json();
        const role=(d.role||'').toUpperCase();
        if(role!=='HOD'){
            const card=document.getElementById('uploadCard');
            if(card) card.innerHTML='<div class="alert alert-warn">Excel result upload is restricted to HOD. Your role: '+role+'</div>';
            const previewBtn=document.getElementById('previewBtn');
            if(previewBtn) previewBtn.disabled=true;
        }
    }catch(e){
        const card=document.getElementById('uploadCard');
        if(card) card.innerHTML='<div class="alert alert-error">Not authenticated. Please login as HOD to upload.</div>';
    }
})();
// Logout — also invalidates server session
if ($('logoutBtn')) $('logoutBtn').addEventListener('click', async () => {
    try { await fetch(`${API_BASE}/api/auth/logout`, { method: 'POST', credentials: 'include' }); } catch(e){}
    localStorage.clear();
    location.href = 'index.html';
});

// ===== File handling =====
const fileInput = $('excelFile');
const browseBtn = $('browseBtn');
const dropZone = $('dropZone');
const fileInfo = $('fileInfo');
const fileNameEl = $('fileName');
const fileSizeEl = $('fileSize');
const fileError = $('fileError');
const clearFileBtn = $('clearFile');

if (browseBtn && fileInput) browseBtn.addEventListener('click', () => fileInput.click());
if (clearFileBtn) clearFileBtn.addEventListener('click', clearFile);

if (fileInput) fileInput.addEventListener('change', e => {
    const f = e.target.files[0];
    handleFile(f);
});

if (dropZone) {
    ['dragenter','dragover'].forEach(ev => dropZone.addEventListener(ev, e => {
        e.preventDefault(); e.stopPropagation();
        dropZone.classList.add('dragover');
    }));
    ['dragleave','drop'].forEach(ev => dropZone.addEventListener(ev, e => {
        e.preventDefault(); e.stopPropagation();
        dropZone.classList.remove('dragover');
    }));
    dropZone.addEventListener('drop', e => {
        const f = e.dataTransfer.files[0];
        handleFile(f);
    });
}

function handleFile(file) {
    hidePreview();
    hideConfirm();
    hideSuccess();
    hideImportError();
    fileError.classList.add('hidden');
    fileError.textContent = '';

    if (!file) return;
    const name = file.name || '';
    const lower = name.toLowerCase();
    if (!lower.endsWith('.xlsx') && !lower.endsWith('.xls')) {
        showFileError('Invalid file type. Please select .xlsx (or .xls) only.');
        selectedFile = null;
        updateChecklist();
        return;
    }
    // Optional: size check 20MB
    if (file.size > 20 * 1024 * 1024) {
        showFileError('File too large (max 20 MB).');
        selectedFile = null;
        updateChecklist();
        return;
    }
    selectedFile = file;
    fileNameEl.textContent = file.name;
    fileSizeEl.textContent = `— ${(file.size/1024).toFixed(1)} KB`;
    fileInfo.classList.remove('hidden');
    updateChecklist();
}

function clearFile() {
    selectedFile = null;
    if (fileInput) fileInput.value = '';
    fileInfo.classList.add('hidden');
    fileNameEl.textContent = '—';
    hidePreview();
    hideConfirm();
    updateChecklist();
}

function showFileError(msg) {
    fileError.textContent = msg;
    fileError.classList.remove('hidden');
}

// ===== Metadata & validation =====
const branchSel = $('branchSelect');
const semSel = $('semesterSelect');
const batchSel = $('batchSelect');
const previewBtn = $('previewBtn');
const validationMsg = $('validationMsg');

[branchSel, semSel, batchSel].forEach(el => {
    if (!el) return;
    el.addEventListener('change', updateChecklist);
    el.addEventListener('input', updateChecklist);
});

function updateChecklist() {
    const branch = branchSel ? branchSel.value.trim() : '';
    const sem = semSel ? semSel.value.trim() : '';
    const batch = batchSel ? batchSel.value.trim() : '';
    const fileOk = !!selectedFile;
    const allOk = !!branch && !!sem && !!batch && fileOk;
    if (previewBtn) previewBtn.disabled = !allOk;
    // Checklist and validationMsg removed per spec #4 — use native * labels and button disabled state only
}

// Initial
updateChecklist();

// ===== Preview =====
if (previewBtn) previewBtn.addEventListener('click', doPreview);

async function doPreview() {
    hidePreview();
    hideConfirm();
    hideSuccess();
    hideImportError();

    const branch = branchSel.value.trim();
    const semester = semSel.value.trim();
    const batch = batchSel.value.trim();

    if (!branch || !semester || !batch || !selectedFile) {
        // Button disabled until valid; guard without showing old checklist warning
        return;
    }

    const previewCard = $('previewCard');
    const previewStatus = $('previewStatus');
    const previewMeta = $('previewMeta');
    const previewUploaderInfo = $('previewUploaderInfo');
    show(previewCard, true);
    show(previewStatus, true);
    previewStatus.textContent = 'Submitting Excel and generating preview — no data will be written to MySQL yet...';
    previewStatus.className = 'alert alert-warn';
    show(previewMeta, false);
    if(previewUploaderInfo) previewUploaderInfo.classList.add('hidden');
    previewBtn.disabled = true;
    previewBtn.textContent = '⏳ Submitting Excel...';

    try {
        const uploadedBy = localStorage.getItem('userId') || localStorage.getItem('usn') || 'unknown';
        const fd = new FormData();
        fd.append('file', selectedFile);
        fd.append('branch', branch);
        fd.append('department', branch);
        fd.append('semester', semester);
        fd.append('batch', batch);
        fd.append('uploadedBy', uploadedBy);
        const resp = await fetch(`${API_BASE}/api/import/preview`, {
            method: 'POST',
            body: fd,
            credentials: 'include'
        });
        const data = await resp.json();
        if (!resp.ok) {
            throw new Error(data.error || data.message || 'Preview failed');
        }
        lastPreview = data;
        renderPreview(data);
    } catch (e) {
        previewStatus.textContent = 'Preview failed: ' + (e.message || e) + '. Ensure backend is running.';
        previewStatus.className = 'alert alert-error';
        console.error(e);
    } finally {
        previewBtn.disabled = false;
        previewBtn.textContent = '📤 Submit Excel';
        updateChecklist();
        if (lastPreview) previewCard.scrollIntoView({behavior:'smooth'});
    }
}

function showValidationAlert(msg) {
    const el = $('validationMsg');
    if (!el) return;
    el.textContent = msg;
    el.classList.remove('hidden');
}

function renderPreview(d) {
    const previewStatus = $('previewStatus');
    const previewMeta = $('previewMeta');
    const previewUploaderInfo = $('previewUploaderInfo');
    if(previewUploaderInfo){
        const uploader = d.uploadedBy || 'unknown';
        const dt = d.uploadedAt ? new Date(d.uploadedAt) : new Date();
        const dateTimeStr = dt.toLocaleString('en-GB', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit', hour12:true });
        previewUploaderInfo.innerHTML=`Uploaded by: ${escapeHtml(uploader)}<br>Uploaded on: ${escapeHtml(dateTimeStr)}`;
        previewUploaderInfo.classList.remove('hidden');
    }

    // Fill meta
    $('p-file').textContent = d.fileName || (selectedFile ? selectedFile.name : '—');
    $('p-branch').textContent = d.branch || d.department || '—';
    $('p-sem').textContent = d.semester || '—';
    $('p-batch').textContent = d.batch || '—';
    $('p-students').textContent = d.studentsDetected ?? 0;
    $('p-subjects').textContent = d.subjectsDetected ?? 0;
    $('p-rows').textContent = d.totalRows ?? 0;

    // Sync preview (18.7)
    const syncBox=$('syncPreviewBox');
    const syncGrid=$('syncPreviewGrid');
    if(syncBox && syncGrid){
        const ns=d.newStudents??0, es=d.existingStudents??0, swc=d.studentsWithChanges??0, sau=d.studentsAlreadyUpToDate??0;
        const nsr=d.newSubjectResults??0, stu=d.subjectResultsToUpdate??0, sau2=d.subjectResultsAlreadyUpToDate??0;
        const ir=d.invalidRows??0, dr=d.duplicateRowsWithinFile??0;
        const setIf=(id,v)=>{ const e=$(id); if(e) e.textContent=v; };
        setIf('p-newStudents', ns); setIf('p-existingStudents', es); setIf('p-studentsWithChanges', swc); setIf('p-studentsUnchanged', sau);
        setIf('p-newSubjects', nsr); setIf('p-subjectToUpdate', stu); setIf('p-subjectUnchanged', sau2);
        setIf('p-invalidRows', ir); setIf('p-duplicateRows', dr);
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

    const sheetInfo = $('sheetInfo');
    if (sheetInfo) sheetInfo.textContent = 'Sheets: ' + (d.sheetNames ? d.sheetNames.join(', ') : '—') + ' | Headers: ' + (d.headers ? d.headers.join(' | ') : '—');

    const parserNoteBox = $('parserNoteBox');
    if (parserNoteBox) {
        if (d.parserNote) {
            parserNoteBox.textContent = d.parserNote;
            parserNoteBox.classList.remove('hidden');
        } else parserNoteBox.classList.add('hidden');
    }

    // Errors / warnings
    const errorBox = $('errorBox');
    const warnBox = $('warnBox');
    if (errorBox) {
        if (d.validationErrors && d.validationErrors.length) {
            errorBox.innerHTML = '<strong>❌ Validation errors (must fix before import):</strong><ul style="margin:6px 0 0; padding-left:18px;">' + d.validationErrors.map(e => `<li>${escapeHtml(e)}</li>`).join('') + '</ul>';
            errorBox.className = 'alert alert-error';
            errorBox.classList.remove('hidden');
        } else { errorBox.classList.add('hidden'); errorBox.innerHTML=''; }
    }
    if (warnBox) {
        if (d.validationWarnings && d.validationWarnings.length) {
            warnBox.innerHTML = '<strong>⚠️ Warnings:</strong><ul style="margin:6px 0 0; padding-left:18px;">' + d.validationWarnings.map(w => `<li>${escapeHtml(w)}</li>`).join('') + '</ul>';
            warnBox.className = 'alert alert-warn';
            warnBox.classList.remove('hidden');
        } else { warnBox.classList.add('hidden'); warnBox.innerHTML=''; }
    }

    // Table preview
    const headEl = $('previewHead');
    const bodyEl = $('previewBody');
    if (headEl && bodyEl) {
        const headers = d.headers || [];
        if (headers.length) {
            headEl.innerHTML = '<tr>' + headers.map(h => `<th>${escapeHtml(h)}</th>`).join('') + '</tr>';
        } else headEl.innerHTML = '<tr><th>No headers detected</th></tr>';

        const rows = d.previewRows || [];
        if (rows.length) {
            bodyEl.innerHTML = rows.map(row => {
                const tds = headers.map(h => {
                    const v = row[h] !== undefined ? row[h] : '';
                    return `<td title="${escapeHtml(String(v))}">${escapeHtml(String(v))}</td>`;
                }).join('');
                return `<tr>${tds}</tr>`;
            }).join('');
        } else {
            bodyEl.innerHTML = `<tr><td colspan="${headers.length || 1}" class="empty">No rows to preview</td></tr>`;
        }
    }

    // Status banner
    if (d.hasErrors) {
        previewStatus.textContent = '❌ Preview finished with errors — please fix the file or metadata before confirming import.';
        previewStatus.className = 'alert alert-error';
    } else if (d.validationWarnings && d.validationWarnings.length) {
        previewStatus.textContent = '⚠️ Preview finished with warnings — you may still continue to confirmation, but invalid rows will be skipped.';
        previewStatus.className = 'alert alert-warn';
    } else {
        previewStatus.textContent = '✅ Preview successful — review the data below and continue to confirmation. No records were written to MySQL.';
        previewStatus.className = 'alert alert-success';
    }
    show(previewStatus, true);
    show(previewMeta, true);

    // Update confirm button enablement: disable if blocking errors and no students
    const confirmBtn = $('confirmBtn');
    if (confirmBtn) {
        // Allow continue even with warnings, but if no students detected, disable
        if ((d.studentsDetected ?? 0) === 0 || d.hasErrors) {
            confirmBtn.disabled = true;
            confirmBtn.title = d.hasErrors ? 'Fix validation errors before continuing' : 'No students detected';
            confirmBtn.style.opacity = '0.5';
        } else {
            confirmBtn.disabled = false;
            confirmBtn.title = '';
            confirmBtn.style.opacity = '1';
        }
    }
}

function hidePreview() {
    const c = $('previewCard');
    if (c) c.classList.add('hidden');
    const ui=$('previewUploaderInfo'); if(ui) ui.classList.add('hidden');
    lastPreview = null;
}
function hideConfirm() {
    const c = $('confirmCard');
    if (c) c.classList.add('hidden');
}
function hideSuccess() {
    const c = $('successCard');
    if (c) c.classList.add('hidden');
}
function hideImportError() {
    const e = $('importError');
    if (e) { e.classList.add('hidden'); e.textContent=''; }
}

// ===== Confirmation flow =====
const confirmBtn = $('confirmBtn');
const editBtn = $('editBtn');
if (editBtn) editBtn.addEventListener('click', () => {
    const metaCard = $('metaCard');
    if (metaCard) metaCard.scrollIntoView({behavior:'smooth'});
});
if (confirmBtn) confirmBtn.addEventListener('click', showConfirm);

function showConfirm() {
    if (!lastPreview) return;
    const card = $('confirmCard');
    const summary = $('confirmSummary');
    if (summary) summary.innerHTML =
        `Department: <strong>${escapeHtml(lastPreview.branch || lastPreview.department || '—')}</strong><br>`+
        `Semester: <strong>${escapeHtml(lastPreview.semester || '—')}</strong><br>`+
        `Batch: <strong>${escapeHtml(lastPreview.batch || '—')}</strong><br>`+
        `Students: <strong>${lastPreview.studentsDetected ?? 0}</strong> (from ${lastPreview.totalRows ?? 0} rows)<br>`+
        `Subjects detected: <strong>${lastPreview.subjectsDetected ?? 0}</strong><br>`+
        `File: <strong>${escapeHtml(lastPreview.fileName || '')}</strong>`;
    show(card, true);
    card.scrollIntoView({behavior:'smooth'});
    hideImportError();
}

const cancelImportBtn = $('cancelImport');
if (cancelImportBtn) cancelImportBtn.addEventListener('click', () => {
    const card = $('confirmCard');
    if (card) card.classList.add('hidden');
});

const doImportBtn = $('doImportBtn');
if (doImportBtn) doImportBtn.addEventListener('click', doImport);

async function doImport() {
    if (!selectedFile || !lastPreview) {
        showImportError('No file or preview available. Please select file and preview first.');
        return;
    }
    const branch = branchSel.value.trim();
    const semester = semSel.value.trim();
    const batch = batchSel.value.trim();

    doImportBtn.disabled = true;
    doImportBtn.textContent = '⏳ Importing...';
    const errEl = $('confirmError');
    if (errEl) { errEl.classList.add('hidden'); errEl.textContent=''; }

    try {
        const uploadedBy = localStorage.getItem('userId') || localStorage.getItem('usn') || 'unknown';
        const fd = new FormData();
        fd.append('file', selectedFile);
        fd.append('branch', branch);
        fd.append('department', branch);
        fd.append('semester', semester);
        fd.append('batch', batch);
        fd.append('uploadedBy', uploadedBy);

        const resp = await fetch(`${API_BASE}/api/import/confirm`, {
            method: 'POST',
            body: fd,
            credentials: 'include'
        });
        const data = await resp.json();

        // data may be success or validation failure (both 200) or error (500)
        if (!resp.ok) {
            // 500 with success:false
            throw new Error(data.message || data.error || 'Import failed');
        }

        if (!data.success) {
            // Validation failure — no records added, but transaction safe
            if (errEl) {
                const list = (data.errors || []).map(e => `<li>${escapeHtml(e)}</li>`).join('');
                errEl.innerHTML = `<strong>❌ Import not completed:</strong> ${escapeHtml(data.message || '')}` + (list ? `<ul style="margin:6px 0 0; padding-left:18px;">${list}</ul>` : '');
                errEl.classList.remove('hidden');
            }
            // Keep confirm card open to let user edit
            return;
        }

        // Success
        showSuccess(data);
        hideConfirm();
        // Hide preview after success? Keep but maybe hide to focus on success
    } catch (e) {
        showImportError('❌ Import failed. No records were added because validation failed or server error: ' + (e.message || e));
        console.error(e);
        if (errEl) {
            errEl.textContent = 'Import failed: ' + (e.message || e);
            errEl.classList.remove('hidden');
        }
    } finally {
        doImportBtn.disabled = false;
        doImportBtn.textContent = '✅ Confirm Import';
    }
}

function showSuccess(data) {
    const card = $('successCard');
    const summary = $('successSummary');
    const sBranch = $('s-branch');
    const sSem = $('s-sem');
    const sBatch = $('s-batch');
    const warnEl = $('successWarnings');
    const syncGrid=$('successSyncGrid');
    const syncBox=$('successSyncBox');
    const sn=data.studentsNew??0, su=data.studentsUpdated??0, sun=data.studentsUnchanged??0;
    const si=data.subjectResultsInserted??0, siu=data.subjectResultsUpdated??0, siun=data.subjectResultsUnchanged??0;
    const sk=data.skippedRows??0, dup=data.duplicateRowsWithinFile??0;
    let msgHtml='';
    if(data.message && data.message.includes('Already uploaded')){
        msgHtml=`<strong>${escapeHtml(data.message)}</strong>`;
    } else if(sn===0 && su===0 && sun>0){
        msgHtml='<strong>Already uploaded — no changes detected.</strong><br>Students already up to date: '+sun;
    } else {
        msgHtml=`Import completed successfully.<br>Students — New: <strong>${sn}</strong> | Updated: <strong>${su}</strong> | Unchanged: <strong>${sun}</strong><br>Subject Results — Inserted: <strong>${si}</strong> | Updated: <strong>${siu}</strong> | Unchanged: <strong>${siun}</strong>`;
        if(sk>0 || dup>0) msgHtml+=`<br>Skipped/Invalid Rows: <strong>${sk}</strong> | Duplicate Rows: <strong>${dup}</strong>`;
    }
    if (summary) summary.innerHTML = msgHtml + (data.message && !data.message.includes('Already uploaded') && !msgHtml.includes(data.message) ? `<br><span class="muted">${escapeHtml(data.message)}</span>` : '');
    if (sBranch) sBranch.textContent = data.branch || '—';
    if (sSem) sSem.textContent = data.semester || '—';
    if (sBatch) sBatch.textContent = data.batch || '—';
    if(syncGrid){
        const setIf=(id,v)=>{ const e=$(id); if(e) e.textContent=v; };
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
    if (warnEl) {
        if (data.errors && data.errors.length) {
            warnEl.innerHTML = '<strong>⚠️ Some rows were skipped:</strong><ul style="margin:6px 0 0; padding-left:18px;">' + data.errors.map(e => `<li>${escapeHtml(e)}</li>`).join('') + '</ul>';
            warnEl.classList.remove('hidden');
        } else warnEl.classList.add('hidden');
    }
    show(card, true);
    card.scrollIntoView({behavior:'smooth'});
}

function showImportError(msg) {
    const el = $('importError');
    if (!el) return;
    el.textContent = msg;
    el.classList.remove('hidden');
    el.scrollIntoView({behavior:'smooth'});
}

const importAnotherBtn = $('importAnotherBtn');
if (importAnotherBtn) importAnotherBtn.addEventListener('click', () => {
    clearFile();
    hidePreview();
    hideConfirm();
    hideSuccess();
    hideImportError();
    if (branchSel) branchSel.value = '';
    if (semSel) semSel.value = '';
    if (batchSel) batchSel.value = '';
    updateChecklist();
    window.scrollTo({top:0, behavior:'smooth'});
});

function escapeHtml(s) {
    if (s == null) return '';
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
