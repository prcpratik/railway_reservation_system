import axios from "axios";

// one axios instance for the whole app
const api = axios.create({
  baseURL: "http://localhost:8080/api",
});

// attach the JWT token (if logged in) to every request
api.interceptors.request.use((config) => {
  const savedUser = localStorage.getItem("user");
  if (savedUser) {
    const { token } = JSON.parse(savedUser);
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;
