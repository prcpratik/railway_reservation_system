import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { toast } from "react-toastify";
import api from "../api";

export default function ResetPassword() {
  // the token comes from the link in the email: /reset-password?token=...
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (password !== confirm) {
      toast.error("The two passwords do not match");
      return;
    }
    try {
      const res = await api.post("/auth/reset-password", { token, newPassword: password });
      toast.success(res.data.message);
      setTimeout(() => navigate("/login"), 2000);
    } catch (err) {
      toast.error(err.response?.data?.message || "Reset failed");
    }
  };

  if (!token) {
    return (
      <div className="card form-card">
        <h2>Reset Password</h2>
        <p className="alert alert-error">
          This page needs a reset link. Please use the link sent to your email.
        </p>
        <p className="hint">
          <Link to="/forgot-password">Request a new link</Link>
        </p>
      </div>
    );
  }

  return (
    <div className="card form-card">
      <h2>Set a New Password</h2>
      <form onSubmit={handleSubmit}>
        <label>New Password</label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="at least 6 characters"
          required
        />
        <label>Confirm New Password</label>
        <input
          type="password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          required
        />
        <button type="submit" className="btn">Reset Password</button>
      </form>
      <p className="hint">
        <Link to="/login">Back to login</Link>
      </p>
    </div>
  );
}
