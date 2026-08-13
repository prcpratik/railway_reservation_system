import { useState } from "react";
import { toast } from "react-toastify";
import api from "../api";

const emptyForm = { name: "", email: "", password: "" };

export default function CreateAdmin() {
  const [form, setForm] = useState(emptyForm);

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (form.password.length < 6) {
      toast.error("Password must be at least 6 characters");
      return;
    }
    try {
      const res = await api.post("/admin/create-admin", form);
      toast.success(`Admin "${res.data.name}" (${res.data.email}) created. They can now log in.`);
      setForm(emptyForm);
    } catch (err) {
      toast.error(err.response?.data?.message || "Could not create admin");
    }
  };

  return (
    <div>
      <h2>Create New Admin</h2>
      <p className="hint">Only an admin can create another admin account.</p>

      <div className="card form-card">
        <form onSubmit={handleSubmit}>
          <label>Name</label>
          <input name="name" value={form.name} onChange={handleChange} required />
          <label>Email</label>
          <input name="email" type="email" value={form.email} onChange={handleChange} required />
          <label>Password</label>
          <input
            name="password"
            type="password"
            value={form.password}
            onChange={handleChange}
            placeholder="at least 6 characters"
            required
          />
          <button type="submit" className="btn">Create Admin</button>
        </form>
      </div>
    </div>
  );
}
