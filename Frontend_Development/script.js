const form = document.getElementById("loginForm");

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const userId = document.getElementById("userId").value.trim();
    const password = document.getElementById("password").value;
    const msg = document.getElementById("loginMessage");

    msg.textContent = "";

    if (!userId || !password) {
        msg.textContent = "Enter User ID and password.";
        msg.style.color = "#dc2626";
        return;
    }

    try {
        const API_BASE = window.APP_CONFIG?.API_BASE_URL || "http://localhost:8081";
        const response = await fetch(`${API_BASE}/api/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: 'include',
            body: JSON.stringify({ userId, password })
        });

        let dataText = await response.text();
        let dataJson = null;
        try{ dataJson = JSON.parse(dataText); }catch(e){}
        const isSuccess = response.ok && (dataJson?.message==='Login Successful' || dataText.trim()==='Login Successful');
        if (!response.ok) {
            const errMsg = dataJson?.message || dataText || "Invalid User ID or password.";
            msg.textContent = errMsg;
            msg.style.color = "#dc2626";
            return;
        }

        if (isSuccess) {
            localStorage.setItem("userId", userId);
            const role = (dataJson?.role||'').toUpperCase();
            if(role) localStorage.setItem("userRole", role);
            if(dataJson?.mustChangePassword) localStorage.setItem("mustChangePassword","true");
            else localStorage.removeItem("mustChangePassword");

            msg.textContent = "Login successful.";
            msg.style.color = "#15803d";

            // Role-aware redirect: get fresh role from /api/auth/me (source of truth)
            setTimeout(async () => {
                try{
                    const meRes = await fetch(`${API_BASE}/api/auth/me`, {credentials:'include'});
                    if(meRes.ok){
                        const me = await meRes.json();
                        const r = (me.role||role||'').toUpperCase();
                        if(r==='HOD'){
                            window.location.href = "dashboard.html";
                            return;
                        } else if(r==='ADMIN'){
                            window.location.href = "dashboard.html";
                            return;
                        } else if(r==='STUDENT'){
                            window.location.href = "dashboard.html";
                            return;
                        }
                    }
                }catch(e){}
                // Fallback: use role from login response
                if(role==='HOD' || role==='ADMIN' || role==='STUDENT'){
                    window.location.href = "dashboard.html";
                } else {
                    window.location.href = "home.html";
                }
            }, 500);

        }

        else {
            const errMsg = dataJson?.message || dataText || "Invalid User ID or password.";
            msg.textContent = errMsg;
            msg.style.color = "#dc2626";
        }

    } catch (error) {
        console.error(error);
        msg.textContent = "Cannot connect to backend.";
        msg.style.color = "#dc2626";
    }
});

document.getElementById("createAccount").addEventListener("click", () => {
    window.location.href = "register.html";
});
document.getElementById("forgotPasswordBtn").addEventListener("click", () => {
    window.location.href = "forgot_password.html";
});
