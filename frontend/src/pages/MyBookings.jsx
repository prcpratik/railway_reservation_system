import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import { FaTicketAlt, FaCreditCard, FaTimes } from "react-icons/fa";
import api from "../api";
import { payForBooking } from "../payment";

const statusClass = {
  PENDING: "status-pending",
  CONFIRMED: "status-confirmed",
  WAITLISTED: "status-waitlisted",
  PARTIALLY_CANCELLED: "status-partial",
  CANCELLED: "status-cancelled",
};

export default function MyBookings() {
  const user = useSelector((state) => state.user);
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [payConfig, setPayConfig] = useState({ enabled: false });

  const loadBookings = async () => {
    try {
      const res = await api.get("/bookings/my");
      setBookings(res.data);
    } catch (err) {
      toast.error(err.response?.data?.message || "Could not load bookings");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBookings();
    api.get("/payments/config").then((res) => setPayConfig(res.data));
  }, []);

  // retry payment for a PENDING booking
  const payNow = (booking) => {
    payForBooking(booking, payConfig.keyId, user, (result) => {
      result.type === "success" ? toast.success(result.text) : toast.error(result.text);
      loadBookings();
    });
  };

  // how much extra was refunded compared to before this cancellation
  const refundText = (booking, updated) => {
    const delta = (updated.refundedAmount || 0) - (booking.refundedAmount || 0);
    return delta > 0 ? ` ₹${delta} refunded to your original payment.` : "";
  };

  const cancelBooking = async (booking) => {
    if (!window.confirm("Cancel the entire booking and get a refund?")) return;
    try {
      const res = await api.put(`/bookings/${booking.id}/cancel`);
      toast.success("Booking cancelled." + refundText(booking, res.data));
      loadBookings();
    } catch (err) {
      toast.error(err.response?.data?.message || "Cancel failed");
    }
  };

  // partial cancellation: only this passenger's seat is cancelled
  const cancelPassenger = async (booking, passenger) => {
    if (!window.confirm(`Cancel ticket of ${passenger.name}?`)) return;
    try {
      const res = await api.put(`/bookings/${booking.id}/passengers/${passenger.id}/cancel`);
      toast.success(`Cancelled ${passenger.name}'s ticket.` + refundText(booking, res.data));
      loadBookings();
    } catch (err) {
      toast.error(err.response?.data?.message || "Cancel failed");
    }
  };

  if (loading) {
    return (
      <div className="state">
        <div className="spinner" />
        Loading your bookings…
      </div>
    );
  }

  return (
    <div>
      <h2>My Bookings</h2>
      {bookings.length === 0 ? (
        <div className="state">
          <div className="state-icon"><FaTicketAlt /></div>
          <div className="state-title">No bookings yet</div>
          Search for a train and book your first ticket.
        </div>
      ) : (
        bookings.map((b) => (
          <div className="card booking-card" key={b.id}>
            <div className="booking-header">
              <div>
                <strong>PNR {b.pnr || b.id}</strong> — {b.trainName}{" "}
                <span className="muted">#{b.trainNumber}</span>
                {b.seatClassLabel && <span className="class-badge">{b.seatClassLabel}</span>}
                <div className="muted">
                  {b.source} → {b.destination} · {b.journeyDate} · departs {b.departureTime}
                  {b.arrivalTime && ` · arrives ${b.arrivalTime}`}
                </div>
                {(b.source !== b.trainSource || b.destination !== b.trainDestination) && (
                  <div className="muted">
                    part journey — train runs {b.trainSource} → {b.trainDestination}
                  </div>
                )}
              </div>
              <div className="booking-header-right">
                <span className={statusClass[b.status]}>{b.status.replace("_", " ")}</span>
                <div className="muted">Total fare: ₹{b.totalFare}</div>
                {b.refundedAmount > 0 && (
                  <div className="refund-note">Refunded ₹{b.refundedAmount}</div>
                )}
                {b.status === "PENDING" && (
                  <button className="btn btn-small" onClick={() => payNow(b)}>
                    <FaCreditCard /> Pay Now
                  </button>
                )}
                {b.status !== "CANCELLED" && (
                  <button className="btn btn-small btn-danger" onClick={() => cancelBooking(b)}>
                    Cancel All
                  </button>
                )}
              </div>
            </div>

            <table className="table passenger-table">
              <thead>
                <tr>
                  <th>Passenger</th>
                  <th>Age</th>
                  <th>Gender</th>
                  <th>Seat</th>
                  <th className="num">Fare</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {b.passengers.map((p) => (
                  <tr key={p.id}>
                    <td data-label="Passenger">{p.name}</td>
                    <td data-label="Age">{p.age}</td>
                    <td data-label="Gender">{p.gender}</td>
                    <td data-label="Seat">
                      {p.status === "WAITLISTED"
                        ? `WL${p.waitlistNumber}`
                        : p.seatNumber
                        ? `S-${p.seatNumber}`
                        : "—"}
                    </td>
                    <td data-label="Fare" className="num">
                      ₹{p.status === "CANCELLED" ? 0 : b.farePerSeat}
                    </td>
                    <td data-label="Status">
                      <span className={statusClass[p.status]}>{p.status}</span>
                    </td>
                    <td>
                      {p.status !== "CANCELLED" && b.status !== "PENDING" && (
                        <button
                          className="btn btn-small btn-danger"
                          onClick={() => cancelPassenger(b, p)}
                        >
                          <FaTimes /> Cancel
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))
      )}
    </div>
  );
}
