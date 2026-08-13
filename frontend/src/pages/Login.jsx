import { useState } from "react";
import { useDispatch } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import api from "../api";
import { loginAction } from "../store";

export default function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const res = await api.post("/auth/login", { email, password });
      localStorage.setItem("user", JSON.stringify(res.data));
      dispatch(loginAction(res.data));
      // admins go to Manage Trains; passengers go to the booking page
      navigate(res.data.role === "ADMIN" ? "/admin" : "/");
    } catch (err) {
      toast.error(err.response?.data?.message || "Login failed");
    }
  };

  return (
    <div className="card form-card">
      <h2>Login</h2>
      <form onSubmit={handleSubmit}>
        <label>Email</label>
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <label>Password</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <button type="submit" className="btn">Login</button>
      </form>
      <p className="hint">
        <Link to="/forgot-password">Forgot password?</Link>
      </p>
      <p className="hint">
        New user? <Link to="/register">Register here</Link>
      </p>
    </div>
  );
}
