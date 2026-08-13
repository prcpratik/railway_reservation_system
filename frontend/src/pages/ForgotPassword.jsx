import { useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import api from "../api";

export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [sending, setSending] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSending(true);
    try {
      const res = await api.post("/auth/forgot-password", { email });
      toast.success(res.data.message);
    } catch (err) {
      toast.error(err.response?.data?.message || "Request failed");
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="card form-card">
      <h2>Forgot Password</h2>
      <p className="hint">
        Enter your registered email and we will send you a link to set a new password.
      </p>
      <form onSubmit={handleSubmit}>
        <label>Email</label>
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <button type="submit" className="btn" disabled={sending}>
          {sending ? "Sending…" : "Send Reset Link"}
        </button>
      </form>
      <p className="hint">
        Remembered it? <Link to="/login">Back to login</Link>
      </p>
    </div>
  );
}
