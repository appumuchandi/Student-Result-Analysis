const API_BASE = window.APP_CONFIG?.API_BASE_URL || "http://localhost:8081";

const PREVIOUS_KEY = 'resultAnalysisPreviousRanking';
let currentStudents = [];
let previousRanking = [];

const $ = (id) => document.getElementById(id);

function number(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function escapeHtml(value) {
  return String(value ?? '--')
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#039;');
}

function rankStudents(data) {
  const enteredUSN = localStorage.getItem('usn');
  if(!enteredUSN){
    return [];
  }
  
  const prefix = enteredUSN.substring(0,7).toUpperCase();
  const filteredStudents = data.filter(student => student.usn && String(student.usn).toUpperCase().startsWith(prefix));

  return filteredStudents.sort((a, b) => {
    const cgpaA = number(a.cgpa) ?? -1;
    const cgpaB = number(b.cgpa) ?? -1;
    if (cgpaB !== cgpaA) return cgpaB - cgpaA;
    return String(a.usn ?? '').localeCompare(String(b.usn ?? ''));
  });
}

function loadPrevious() {
  try {
    const saved = JSON.parse(localStorage.getItem(PREVIOUS_KEY) || '[]');
    return Array.isArray(saved) ? saved : [];
  } catch (_) { return []; }
}

function savePrevious(ranked) {
  const snapshot = ranked.map((s, i) => ({ usn: s.usn, rank: i + 1 }));
  localStorage.setItem(PREVIOUS_KEY, JSON.stringify(snapshot));
}

function getPreviousRank(usn) {
  const found = previousRanking.find(x => String(x.usn).toLowerCase() === String(usn).toLowerCase());
  return found ? found.rank : null;
}

function rankChangeText(currentRank, oldRank) {
  if (oldRank === null) return '<span class="muted">New</span>';
  const change = oldRank - currentRank;
  if (change > 0) return `<span class="status-pass">↑ ${change}</span>`;
  if (change < 0) return `<span class="status-fail">↓ ${Math.abs(change)}</span>`;
  return '<span class="muted">— Same</span>';
}

function drawBarChart(canvas, labels, values, options = {}) {
  const ctx = canvas.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const width = Math.max(canvas.parentElement.clientWidth, 320);
  const height = options.height || 300;
  canvas.width = width * dpr;
  canvas.height = height * dpr;
  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);

  const pad = { left: 48, right: 18, top: 28, bottom: 62 };
  const plotW = width - pad.left - pad.right;
  const plotH = height - pad.top - pad.bottom;
  const max = Math.max(...values, 1);
  const step = plotW / Math.max(values.length, 1);
  const barW = Math.min(58, step * 0.58);

  ctx.font = '12px Segoe UI, Arial';
  ctx.textAlign = 'right';
  ctx.fillStyle = '#667085';
  for (let i = 0; i <= 4; i++) {
    const y = pad.top + plotH - (plotH * i / 4);
    const value = (max * i / 4).toFixed(options.decimals ?? 1);
    ctx.fillText(value, pad.left - 8, y + 4);
    ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(width - pad.right, y); ctx.strokeStyle = '#e6ebf2'; ctx.stroke();
  }

  values.forEach((value, i) => {
    const x = pad.left + step * i + (step - barW) / 2;
    const h = (value / max) * plotH;
    const y = pad.top + plotH - h;
    ctx.fillStyle = i % 2 ? '#7c3aed' : '#2563eb';
    ctx.fillRect(x, y, barW, h);
    ctx.fillStyle = '#172033'; ctx.textAlign = 'center';
    ctx.font = 'bold 12px Segoe UI, Arial';
    ctx.fillText(options.valueLabels?.[i] ?? String(value), x + barW / 2, Math.max(y - 7, 14));
    ctx.font = '11px Segoe UI, Arial'; ctx.fillStyle = '#667085';
    const label = String(labels[i]);
    ctx.save(); ctx.translate(x + barW / 2, height - 25); ctx.rotate(-0.28); ctx.fillText(label, 0, 0); ctx.restore();
  });
}

function drawComparisonChart(current) {
  const top = current.slice(0, 8);
  const labels = top.map(s => s.usn || s.name || '--');
  const currentRanks = top.map((_, i) => i + 1);
  const previousRanks = top.map(s => getPreviousRank(s.usn) ?? currentRanks[top.indexOf(s)]);
  const canvas = $('rankComparisonChart');
  drawGroupedChart(canvas, labels, previousRanks, currentRanks);
}

function drawGroupedChart(canvas, labels, previous, current) {
  const ctx = canvas.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const width = Math.max(canvas.parentElement.clientWidth, 320);
  const height = 300;
  canvas.width = width * dpr; canvas.height = height * dpr;
  canvas.style.width = `${width}px`; canvas.style.height = `${height}px`;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);
  const pad = { left: 48, right: 18, top: 42, bottom: 72 }; const plotW = width - pad.left - pad.right; const plotH = height - pad.top - pad.bottom;
  const max = Math.max(...previous, ...current, 1); const step = plotW / Math.max(labels.length, 1); const barW = Math.min(22, step * 0.28);
  ctx.font = '11px Segoe UI,Arial'; ctx.textAlign = 'right'; ctx.fillStyle = '#667085';
  for (let i = 0; i <= max; i++) { if (max > 12 && i % 2) continue; const y = pad.top + plotH - (i / max) * plotH; ctx.fillText(String(i), pad.left - 8, y + 4); ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(width - pad.right, y); ctx.strokeStyle = '#e6ebf2'; ctx.stroke(); }
  labels.forEach((label, i) => {
    const base = pad.left + i * step + (step / 2); const vals = [previous[i], current[i]];
    vals.forEach((v, j) => { const h = (v / max) * plotH; const x = base - barW - (barW / 4) + j * (barW + barW / 2); const y = pad.top + plotH - h; ctx.fillStyle = j === 0 ? '#94a3b8' : '#2563eb'; ctx.fillRect(x, y, barW, h); });
    ctx.fillStyle = '#667085'; ctx.textAlign = 'center'; ctx.save(); ctx.translate(base, height - 28); ctx.rotate(-0.35); ctx.fillText(String(label), 0, 0); ctx.restore();
  });
  ctx.textAlign = 'left'; ctx.fillStyle = '#667085'; ctx.fillRect(pad.left, 15, 12, 12); ctx.fillText('Previous', pad.left + 18, 25); ctx.fillStyle = '#2563eb'; ctx.fillRect(pad.left + 90, 15, 12, 12); ctx.fillStyle = '#667085'; ctx.fillText('Current', pad.left + 108, 25);
}

function drawDepartmentChart(ranked) {
  const groups = {};
  ranked.forEach(s => {
    const dept = s.branch || 'Unknown';
    const cgpa = number(s.cgpa);
    if (!groups[dept]) groups[dept] = { total: 0, count: 0, students: 0 };
    groups[dept].students++;
    if (cgpa !== null) { groups[dept].total += cgpa; groups[dept].count++; }
  });
  const entries = Object.entries(groups).map(([dept, v]) => ({ dept, avg: v.count ? v.total / v.count : 0, students: v.students })).sort((a, b) => b.avg - a.avg);
  const labels = entries.map(x => x.dept); const values = entries.map(x => x.avg);
  drawBarChart($('departmentChart'), labels, values, { height: 300, decimals: 1, valueLabels: values.map(v => v.toFixed(2)) });
}

function renderStats(ranked) {
  const valid = ranked.map(s => number(s.cgpa)).filter(v => v !== null);
  const avg = valid.length ? valid.reduce((a, b) => a + b, 0) / valid.length : null;
  const departments = new Set(ranked.map(s => s.branch).filter(Boolean)).size;
  $('rankStats').innerHTML = `
    <div class="summary"><small>Total Students</small><strong>${ranked.length}</strong></div>
    <div class="summary"><small>Top CGPA</small><strong>${valid.length ? Math.max(...valid).toFixed(2) : '--'}</strong></div>
    <div class="summary"><small>Average CGPA</small><strong>${avg !== null ? avg.toFixed(2) : '--'}</strong></div>
    <div class="summary"><small>Departments</small><strong>${departments}</strong></div>`;
}

function renderTable(ranked) {
  const table = $('rankTable');
  if (!ranked.length) { table.innerHTML = '<tr><td colspan="7" class="empty">No students found.</td></tr>'; return; }
  table.innerHTML = ranked.map((s, i) => {
    const rank = i + 1; const old = getPreviousRank(s.usn);
    const result = String(s.result ?? '--').toUpperCase();
    const resultClass = result === 'PASS' ? 'status-pass' : result === 'FAIL' ? 'status-fail' : '';
    return `<tr><td><strong>${rank}</strong></td><td>${escapeHtml(s.usn)}</td><td>${escapeHtml(s.name)}</td><td>${escapeHtml(s.branch)}</td><td>${number(s.cgpa)?.toFixed(2) ?? '--'}</td><td class="${resultClass}">${escapeHtml(result)}</td><td>${rankChangeText(rank, old)}</td></tr>`;
  }).join('');
}

async function loadRanks() {
  const status = $('resultStatus'); const table = $('rankTable');
  status.textContent = 'Loading student ranking from the backend...';
  table.innerHTML = '<tr><td colspan="7" class="empty">Loading...</td></tr>';
  previousRanking = loadPrevious();
  try {
    const response = await fetch(`${API_BASE}/students`);
    if (!response.ok) throw new Error('Backend request failed');
    const data = await response.json();
    currentStudents = rankStudents(Array.isArray(data) ? data : []);
    renderTable(currentStudents); renderStats(currentStudents); renderTopStudent(currentStudents); drawComparisonChart(currentStudents); drawDepartmentChart(currentStudents);
    status.textContent = `${currentStudents.length} student(s) ranked successfully.`;
    $('previousNote').textContent = previousRanking.length ? 'Comparison uses the ranking saved from the previous successful load.' : 'No previous ranking was saved yet. Refresh after data changes to compare ranks.';
    // Save AFTER rendering so the current load can be compared with the previous load.
    savePrevious(currentStudents);
  } catch (error) {
    status.textContent = 'Backend unavailable. Start Spring Boot on port 8081 and refresh.';
    table.innerHTML = '<tr><td colspan="7" class="empty">Could not load students. Check that the backend is running.</td></tr>';
  }
}

function showPanel(panelId) {
  document.querySelectorAll('.analysis-panel').forEach(panel => panel.classList.add('hidden'));
  const panel = $(panelId);
  if (panel) panel.classList.remove('hidden');
  if (panelId === 'rankComparePanel' && currentStudents.length) drawComparisonChart(currentStudents);
  if (panelId === 'deptPanel' && currentStudents.length) drawDepartmentChart(currentStudents);
  panel?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderTopStudent(ranked) {
  const box = $('topStudent');
  if (!ranked.length) { box.innerHTML = ''; return; }
  const top = ranked[0];
  const cgpa = number(top.cgpa);
  box.innerHTML = `
    <div class="topper-highlight">
      <div class="topper-medal">🏆</div>
      <div>
        <small>Current Top Student</small>
        <h3>${escapeHtml(top.name)}</h3>
        <p>${escapeHtml(top.usn)} • ${escapeHtml(top.branch)} • CGPA: <strong>${cgpa !== null ? cgpa.toFixed(2) : '--'}</strong></p>
      </div>
      <div class="top-rank">Rank #1</div>
    </div>`;
}

async function downloadExcel() {
  const btn = $('excelBtnMain');
  const status = $('resultStatus');
  const originalText = btn ? btn.textContent : '';

  if (!currentStudents.length) {
    const msg = 'No student data available to download. Ensure backend has student records.';
    if (status) { status.textContent = msg; status.style.background='#fee2e2'; status.style.color='#991b1b'; }
    else alert(msg);
    return;
  }

  if (btn) { btn.textContent = '⏳ Generating Excel...'; btn.disabled = true; }
  if (status) { status.textContent = 'Generating real Excel (.xlsx) via backend Apache POI...'; status.style.background='#eff6ff'; status.style.color='#1e40af'; }

  try {
    // Download genuine .xlsx from backend – filtered by current prefix if needed, but exports all accessible via filter params
    // For Rank Analysis, we export all students; backend will produce real Apache POI xlsx
    const response = await fetch(`${API_BASE}/students/export/excel`);
    if (!response.ok) {
        const txt = await response.text();
        throw new Error(txt || 'Backend returned ' + response.status);
    }
    const blob = await response.blob();
    // Validate blob is not CSV and has xlsx magic (PK zip header)
    const headerCheck = await blob.slice(0, 2).text().catch(()=> '');
    const ct = response.headers.get('Content-Type') || '';

    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const disposition = response.headers.get('Content-Disposition');
    let filename = 'College_Result_Analysis.xlsx';
    if (disposition && disposition.includes('filename=')) {
        const m = disposition.match(/filename="?([^"]+)"?/);
        if (m) filename = m[1];
    }
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    if (status) { status.textContent = `Excel downloaded: ${filename} (${(blob.size/1024).toFixed(1)} KB) — genuine .xlsx generated by Apache POI.`; status.style.background='#f0fdf4'; status.style.color='#15803d'; }
  } catch (e) {
    console.error(e);
    const msg = 'Excel download failed: ' + (e.message || 'Unknown error') + '. Ensure Spring Boot is running on port 8081.';
    if (status) { status.textContent = msg; status.style.background='#fee2e2'; status.style.color='#991b1b'; }
    else alert(msg);
  } finally {
    if (btn) { btn.textContent = originalText; btn.disabled = false; }
  }
}

$('backBtn').addEventListener('click', () => location.href = 'dashboard.html');
$('logoutBtn').addEventListener('click', async () => { try { await fetch(`${API_BASE}/api/auth/logout`, { method: 'POST', credentials: 'include' }); } catch(e){} localStorage.clear(); location.href = 'index.html'; });
$('topperBtn').addEventListener('click', () => showPanel('topperPanel'));
$('rankCompareBtn').addEventListener('click', () => showPanel('rankComparePanel'));
$('deptBtn').addEventListener('click', () => showPanel('deptPanel'));
$('statsBtn').addEventListener('click', () => showPanel('statsPanel'));

// Download menu toggle
const downloadMenu = $('downloadMenu');
const downloadToggle = $('downloadToggle');
if (downloadToggle && downloadMenu) {
  downloadToggle.addEventListener('click', (e) => {
    e.stopPropagation();
    downloadMenu.classList.toggle('open');
  });
  document.addEventListener('click', () => downloadMenu.classList.remove('open'));
}
$('excelBtnMain')?.addEventListener('click', () => { if(downloadMenu) downloadMenu.classList.remove('open'); downloadExcel(); });
$('pdfBtnMain')?.addEventListener('click', () => { if(downloadMenu) downloadMenu.classList.remove('open'); downloadPdf(); });

// Subject-wise Batch Comparison
const compareBtn = $('compareBtn');
if (compareBtn) compareBtn.addEventListener('click', doBatchCompare);

async function doBatchCompare(){
  const curr = ($('currentBatchSelect')?.value||'').trim();
  const prev = ($('previousBatchSelect')?.value||'').trim();
  const branch = ($('compareBranchSelect')?.value||'').trim();
  const semester = ($('compareSemesterSelect')?.value||'').trim();
  const subject = ($('compareSubjectSelect')?.value||'').trim();
  const statusEl = $('compareStatus');
  const bodyEl = $('compareBody');
  const summaryEl = $('compareSummary');
  function showStatus(msg, isError){
    if(!statusEl) return;
    statusEl.textContent=msg;
    statusEl.className=isError?'notice':'notice';
    statusEl.style.background=isError?'#fee2e2':'#eff6ff';
    statusEl.style.color=isError?'#991b1b':'#1e40af';
    statusEl.classList.remove('hidden');
  }
  if(!curr || !prev || !branch || !semester || !subject){
    showStatus('Please select Current Batch, Previous Batch, Branch, Semester and Subject before comparing.', true);
    return;
  }
  showStatus('Loading batch comparison for '+subject+' — '+curr+' vs '+prev+' ...', false);
  if(summaryEl) summaryEl.classList.add('hidden');
  if(bodyEl) bodyEl.innerHTML='<tr><td colspan="7" class="empty">Loading...</td></tr>';
  try{
    const params = new URLSearchParams({currentBatch:curr, previousBatch:prev, branch:branch, semester:semester, subject:subject});
    const resp = await fetch(`${API_BASE}/api/compare/subjects?${params.toString()}`);
    if(!resp.ok){
      const txt = await resp.text();
      throw new Error(txt || 'Backend returned '+resp.status);
    }
    const data = await resp.json();
    // Render summary
    if(summaryEl){
      summaryEl.innerHTML = `
        <div class="summary"><small>Current Batch Avg</small><strong>${data.currentBatchAverage!=null?data.currentBatchAverage.toFixed(2):'--'}</strong></div>
        <div class="summary"><small>Previous Batch Avg</small><strong>${data.previousBatchAverage!=null?data.previousBatchAverage.toFixed(2):'--'}</strong></div>
        <div class="summary"><small>Avg Difference</small><strong>${data.averageDifference!=null?(data.averageDifference>0?'+'+data.averageDifference.toFixed(2):data.averageDifference.toFixed(2)):'--'}</strong></div>
        <div class="summary"><small>Matched Students</small><strong>${data.matchedStudents??0}</strong></div>
        <div class="summary"><small>Unmatched Cur/Prev</small><strong>${data.unmatchedCurrent??0} / ${data.unmatchedPrevious??0}</strong></div>
      `;
      summaryEl.classList.remove('hidden');
    }
    if(!data.comparisons || !data.comparisons.length){
      showStatus('No matching students/subjects found for the selected criteria. Check batches, branch, semester and subject.', true);
      if(bodyEl) bodyEl.innerHTML='<tr><td colspan="7" class="empty">No comparisons found. Try different batches or subject.</td></tr>';
      return;
    }
    showStatus(`Found ${data.comparisons.length} comparison(s) for ${subject} — ${curr} vs ${prev}. Difference = Current − Previous.`, false);
    if(bodyEl){
      bodyEl.innerHTML = data.comparisons.map(row=>{
        const diff = row.difference;
        let diffHtml = '--';
        if(diff!=null){
          if(diff>0) diffHtml=`<span class="status-pass">+${diff}</span>`;
          else if(diff<0) diffHtml=`<span class="status-fail">${diff}</span>`;
          else diffHtml=`<span class="muted">0</span>`;
        }
        return `<tr>
          <td>${escapeHtml(row.currentUsn||'--')}</td>
          <td>${escapeHtml(row.previousUsn||'--')}</td>
          <td>${escapeHtml(row.subjectCode||row.subjectName||'--')}</td>
          <td>${row.currentMarks!=null?row.currentMarks:'--'}</td>
          <td>${row.previousMarks!=null?row.previousMarks:'--'}</td>
          <td>${diffHtml}</td>
          <td>${escapeHtml((row.currentRe||'--')+' / '+(row.previousRe||'--'))}</td>
        </tr>`;
      }).join('');
    }
  }catch(e){
    console.error(e);
    showStatus('Comparison failed: '+(e.message||'Unknown error')+'. Ensure backend is running and batches contain data.', true);
    if(bodyEl) bodyEl.innerHTML='<tr><td colspan="7" class="empty">Error loading comparison.</td></tr>';
  }
}

async function downloadPdf(){
  const btn = $('pdfBtnMain');
  const status = $('resultStatus');
  const originalText = btn ? btn.textContent : '';
  if(btn){ btn.textContent='⏳ Generating PDF...'; btn.disabled=true; }
  if(status){ status.textContent='Generating real PDF (.pdf) via backend OpenPDF...'; status.style.background='#eff6ff'; status.style.color='#1e40af'; }
  try{
    const response = await fetch(`${API_BASE}/api/export/pdf`);
    if(!response.ok){
      const txt = await response.text();
      throw new Error(txt || 'Backend returned '+response.status);
    }
    const blob = await response.blob();
    const ct = response.headers.get('Content-Type')||'';
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href=url;
    const disposition = response.headers.get('Content-Disposition');
    let filename='Rank_Analysis.pdf';
    if(disposition && disposition.includes('filename=')){
      const m=disposition.match(/filename="?([^"]+)"?/);
      if(m) filename=m[1];
    }
    link.download=filename;
    document.body.appendChild(link); link.click(); document.body.removeChild(link); URL.revokeObjectURL(url);
    if(status){ status.textContent=`PDF downloaded: ${filename} (${(blob.size/1024).toFixed(1)} KB) — genuine PDF generated by OpenPDF.`; status.style.background='#f0fdf4'; status.style.color='#15803d'; }
  }catch(e){
    console.error(e);
    const msg='PDF download failed: '+(e.message||'Unknown error')+'. Ensure Spring Boot is running on port 8081.';
    if(status){ status.textContent=msg; status.style.background='#fee2e2'; status.style.color='#991b1b'; }
    else alert(msg);
  }finally{
    if(btn){ btn.textContent=originalText; btn.disabled=false; }
  }
}
document.querySelectorAll('.closePanel').forEach(btn => btn.addEventListener('click', () => { 
  document.querySelectorAll('.analysis-panel').forEach(panel => panel.classList.add('hidden')); window.scrollTo({ top: 0, behavior: 'smooth' });
}));
window.addEventListener('resize', () => {
  if (currentStudents.length) {
    if (!$('rankComparePanel').classList.contains('hidden')) drawComparisonChart(currentStudents);
    if (!$('deptPanel').classList.contains('hidden')) drawDepartmentChart(currentStudents);
  }
});
loadRanks();
