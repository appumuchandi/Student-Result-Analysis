document.getElementById("registerForm").addEventListener("submit", async function (event) {
    event.preventDefault();

    const name = document.getElementById("name").value.trim();
    const phone = document.getElementById("phone").value.trim();
    const userId = document.getElementById("regUserId").value.trim();
    const password = document.getElementById("regPassword").value;
    const confirmPassword = document.getElementById("confirmPassword").value;
    const message = document.getElementById("registerMessage");

    message.textContent = "";
    message.style.color = "";

    if (password !== confirmPassword) {
        message.textContent = "Passwords do not match.";
        message.style.color = "#dc2626";
        return;
    }

    if (!/^\d{10}$/.test(phone)) {
        message.textContent = "Enter a valid 10-digit phone number.";
        message.style.color = "#dc2626";
        return;
    }

    if (password.length < 6) {
        message.textContent = "Password must contain at least 6 characters.";
        message.style.color = "#dc2626";
        return;
    }

    try {
        const API_BASE = window.APP_CONFIG?.API_BASE_URL || "http://localhost:8081";
        const response = await fetch(`${API_BASE}/api/auth/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, phone, userId, password })
        });

        const data = await response.text();

        if (!response.ok) {
            throw new Error(data || "Registration failed.");
        }

        message.textContent = data;
        message.style.color = "#15803d";

        setTimeout(() => {
            window.location.href = "index.html";
        }, 1200);

    } catch (error) {
        message.textContent = error.message || "Registration failed. Check the backend.";
        message.style.color = "#dc2626";
    }
});

document.getElementById("backLogin").addEventListener("click", function () {
    window.location.href = "index.html";
});
