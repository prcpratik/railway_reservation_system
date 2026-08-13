import { useEffect, useState } from "react";
import { useDispatch } from "react-redux";
import { toast } from "react-toastify";
import { FaUserCircle, FaKey } from "react-icons/fa";
import api from "../api";
import { updateUserAction } from "../store";

const today = new Date().toISOString().split("T")[0];

const statusClass = {
  PENDING: "status-pending",
  CONFIRMED: "status-confirmed",
  WAITLISTED: "status-waitlisted",
  PARTIALLY_CANCELLED: "status-partial",
  CANCELLED: "status-cancelled",
};

export default function Profile() {
  const dispatch = useDispatch();
  const [profile, setProfile] = useState(null);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [pwd, setPwd] = useState({ currentPassword: "", newPassword: "" });
  const [bookings, setBookings] = useState([]);

  useEffect(() => {
    api.get("/auth/me").then((res) => {
      setProfile(res.data);
      setName(res.data.name);
      setPhone(res.data.phone || "");
      // admins don't book tickets, so skip loading booking history
      if (res.data.role !== "ADMIN") {
        api.get("/bookings/my").then((r) => setBookings(r.data));
      }
    });
  }, []);

  const saveProfile = async (e) => {
    e.preventDefault();
    try {
      const res = await api.put("/auth/profile", { name, phone });
      setProfile(res.data);
      // keep the navbar + stored user in sync with the new name
      dispatch(updateUserAction({ name: res.data.name }));
      const stored = JSON.parse(localStorage.getItem("user"));
      localStorage.setItem("user", JSON.stringify({ ...stored, name: res.data.name }));
      toast.success("Profile updated");
    } catch (err) {
      toast.error(err.response?.data?.message || "Update failed");
    }
  };

  const changePassword = async (e) => {
    e.preventDefault();
    try {
      const res = await api.put("/auth/password", pwd);
      setPwd({ currentPassword: "", newPassword: "" });
      toast.success(res.data.message);
    } catch (err) {
      toast.error(err.response?.data?.message || "Password change failed");
    }
  };

  if (!profile) {
    return (
      <div className="state">
        <div className="spinner" />
        Loading profile…
      </div>
    );
  }

  // split booking history: upcoming (journey today or later) vs past
  const upcoming = bookings.filter((b) => b.journeyDate >= today);
  const past = bookings.filter((b) => b.journeyDate < today);

  return (
    <div>
      <h2>My Profile</h2>

      <div className="profile-grid">
        <form className="card" onSubmit={saveProfile}>
          <h3><FaUserCircle /> Account Details</h3>
          <label>Name</label>
          <input value={name} onChange={(e) => setName(e.target.value)} required />
          <label>Email</label>
          <input value={profile.email} readOnly className="readonly" title="Email cannot be changed" />
          <label>Phone</label>
          <input
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="e.g. 9876543210"
            maxLength={15}
          />
          <label>Role</label>
          <input value={profile.role} readOnly className="readonly" />
          <button type="submit" className="btn">Save Changes</button>
        </form>

        <form className="card" onSubmit={changePassword}>
          <h3><FaKey /> Change Password</h3>
          <label>Current Password</label>
          <input
            type="password"
            value={pwd.currentPassword}
            onChange={(e) => setPwd({ ...pwd, currentPassword: e.target.value })}
            required
          />
          <label>New Password</label>
          <input
            type="password"
            value={pwd.newPassword}
            onChange={(e) => setPwd({ ...pwd, newPassword: e.target.value })}
            required
          />
          <button type="submit" className="btn">Update Password</button>
        </form>
      </div>

      {profile.role !== "ADMIN" && (
        <>
          <h3 className="section-title">Booking History</h3>
          <BookingList title="Upcoming Journeys" list={upcoming} empty="No upcoming journeys." />
          <BookingList title="Past Journeys" list={past} empty="No past journeys yet." />
        </>
      )}
    </div>
  );
}

// read-only summary of bookings (management actions stay on the My Bookings page)
function BookingList({ title, list, empty }) {
  return (
    <div className="history-block">
      <h4>{title} ({list.length})</h4>
      {list.length === 0 ? (
        <p className="hint">{empty}</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>PNR</th>
              <th>Train</th>
              <th>Journey</th>
              <th>Class</th>
              <th>Date</th>
              <th>Passengers</th>
              <th className="num">Fare</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {list.map((b) => (
              <tr key={b.id}>
                <td data-label="PNR">{b.pnr || b.id}</td>
                <td data-label="Train">{b.trainName}</td>
                <td data-label="Journey">{b.source} → {b.destination}</td>
                <td data-label="Class">
                  {b.seatClassLabel && <span className="class-badge">{b.seatClassLabel}</span>}
                </td>
                <td data-label="Date">{b.journeyDate}</td>
                <td data-label="Passengers">{b.passengers.length}</td>
                <td data-label="Fare" className="num">₹{b.totalFare}</td>
                <td data-label="Status">
                  <span className={statusClass[b.status]}>{b.status.replace("_", " ")}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
