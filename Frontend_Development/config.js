// Frontend API base URL — configurable for shared deployment
// For local development keep http://localhost:8081
// For LAN/shared deployment set to server LAN IP or deployed host, e.g. http://192.168.1.50:8081 or https://api.example.com
// MySQL credentials are NEVER exposed here; browser only talks to backend API.
window.APP_CONFIG = window.APP_CONFIG || {
  API_BASE_URL: "http://localhost:8081"
};
