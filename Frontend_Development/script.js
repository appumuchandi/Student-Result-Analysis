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

        const data = await response.text();

        if (!response.ok) {
            msg.textContent = data || "Invalid User ID or password.";
            msg.style.color = "#dc2626";
            return;
        }

        if (data.trim() === "Login Successful" || data.trim() === "Login Successful") {
            localStorage.setItem("userId", userId);

            msg.textContent = "Login successful.";
            msg.style.color = "#15803d";

            setTimeout(() => {
                window.location.href = "home.html";
            }, 500);

        }

        else {
            msg.textContent = data || "Invalid User ID or password.";
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
