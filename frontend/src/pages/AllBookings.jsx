import { useEffect, useState } from "react";
import { toast } from "react-toastify";
import { FaClipboardList } from "react-icons/fa";
import api from "../api";

const statusClass = {
  PENDING: "status-pending",
  CONFIRMED: "status-confirmed",
  WAITLISTED: "status-waitlisted",
  PARTIALLY_CANCELLED: "status-partial",
  CANCELLED: "status-cancelled",
};

export default function AllBookings() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .get("/bookings/all")
      .then((res) => setBookings(res.data))
      .catch((err) => toast.error(err.response?.data?.message || "Could not load bookings"))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="state">
        <div className="spinner" />
        Loading all bookings…
      </div>
    );
  }

  return (
    <div>
      <h2>All Bookings ({bookings.length})</h2>
      {bookings.length === 0 ? (
        <div className="state">
          <div className="state-icon"><FaClipboardList /></div>
          <div className="state-title">No bookings in the system yet</div>
          Bookings made by passengers will appear here.
        </div>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>PNR</th>
              <th>Booked By</th>
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
            {bookings.map((b) => (
              <tr key={b.id}>
                <td data-label="PNR">{b.pnr || b.id}</td>
                <td data-label="Booked By">
                  {b.userName}
                  <div className="muted">{b.userEmail}</div>
                </td>
                <td data-label="Train">
                  {b.trainName}
                  <div className="muted">#{b.trainNumber}</div>
                </td>
                <td data-label="Journey">{b.source} → {b.destination}</td>
                <td data-label="Class">
                  {b.seatClassLabel && <span className="class-badge">{b.seatClassLabel}</span>}
                </td>
                <td data-label="Date">{b.journeyDate}</td>
                <td data-label="Passengers">
                  {b.passengers.length}
                  <div className="muted">{b.passengers.map((p) => p.name).join(", ")}</div>
                </td>
                <td data-label="Fare" className="num">
                  ₹{b.totalFare}
                  {b.refundedAmount > 0 && (
                    <div className="refund-note">Refunded ₹{b.refundedAmount}</div>
                  )}
                </td>
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
