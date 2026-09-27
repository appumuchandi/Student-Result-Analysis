const API_BASE = window.APP_CONFIG?.API_BASE_URL || "http://localhost:8081";

let captcha = '';

function generateCaptcha() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&*?';
    captcha = Array.from({ length: 5 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
    document.getElementById('captchaText').textContent = captcha;
}
generateCaptcha();
document.getElementById('refreshCaptcha').addEventListener('click', generateCaptcha);
// HOD-only import card
(async()=>{
    const card=document.getElementById('hodImportCard');
    if(!card) return;
    try{
        const r=await fetch(`${API_BASE}/api/auth/me`, {credentials:'include'});
        if(!r.ok) return;
        const d=await r.json();
        if((d.role||'').toUpperCase()==='HOD') card.classList.remove('hidden');
    }catch(e){}
})();
document.getElementById('studentForm').addEventListener('submit', async e => {
    e.preventDefault();
    const collegeCode = document.getElementById('collegeCode').value.trim();
    const branch = document.getElementById('branch').value.trim();
    const usn = document.getElementById('usn').value.trim().toUpperCase();
    const cap = document.getElementById('captchaInput').value.trim();
    const msg = document.getElementById('message');

    if (cap !== captcha) {
        msg.textContent = 'Invalid CAPTCHA. Try again.';
        msg.style.color = '#dc2626';
        generateCaptcha();
        return
    }
    msg.textContent = 'Checking student...';
    msg.style.color = '#667085';
    try {
        const r = await fetch(`${API_BASE}/students/usn/${encodeURIComponent(usn)}?collegeCode=${encodeURIComponent(collegeCode)}&branch=${encodeURIComponent(branch)}`);

        if (!r.ok) throw new Error('Student not found');
        const student = await r.json();
        if (student.branch && branch && student.branch.toUpperCase() !== branch.toUpperCase()) {
            throw new Error('Branch does not match this USN.')
        }
        localStorage.setItem('collegeCode', collegeCode);
        localStorage.setItem('branch', branch);
        localStorage.setItem('usn', usn); msg.textContent = 'Student found. Opening dashboard...';
        msg.style.color = '#15803d';

        setTimeout(() => location.href = 'dashboard.html', 400)
    }

    catch (err) {
        msg.textContent = err.message.includes('fetch') ? 'Backend is not reachable. Start Spring Boot on port 8081.' : err.message;
        msg.style.color = '#dc2626'
    }
});

document.getElementById('logoutBtn').addEventListener('click', async () => {
    try { await fetch(`${API_BASE}/api/auth/logout`, { method: 'POST', credentials: 'include' }); } catch(e){}
    localStorage.clear();
    location.href = 'index.html';
});
