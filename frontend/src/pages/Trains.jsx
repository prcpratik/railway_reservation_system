import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { Link } from "react-router-dom";
import { toast } from "react-toastify";
import { FaSearch, FaPlus, FaTimes, FaTrain } from "react-icons/fa";
import api from "../api";
import { payForBooking } from "../payment";

const today = new Date().toISOString().split("T")[0];

// backend sends days as MONDAY, TUESDAY, ... in Set order (not necessarily sorted)
const DAY_ORDER = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const DAY_SHORT = { MONDAY: "Mon", TUESDAY: "Tue", WEDNESDAY: "Wed", THURSDAY: "Thu", FRIDAY: "Fri", SATURDAY: "Sat", SUNDAY: "Sun" };
const formatRunDays = (days) =>
  DAY_ORDER.filter((d) => days.includes(d)).map((d) => DAY_SHORT[d]).join(", ");
const emptyPassenger = { name: "", age: "", gender: "Male" };
const MAX_PASSENGERS = 6;
const MAX_WAITLIST = 10;

export default function Trains() {
  const user = useSelector((state) => state.user);
  const [source, setSource] = useState("");
  const [destination, setDestination] = useState("");
  const [date, setDate] = useState(today);
  const [trains, setTrains] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searched, setSearched] = useState(false);
  // which train + class is being booked
  const [booking, setBooking] = useState(null);
  const [passengers, setPassengers] = useState([]);
  const [payConfig, setPayConfig] = useState({ enabled: false });

  const loadTrains = async () => {
    setLoading(true);
    try {
      const res = await api.get("/trains", { params: { source, destination, date } });
      setTrains(res.data);
    } catch (err) {
      toast.error(err.response?.data?.message || "Could not load trains");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTrains();
    if (user) {
      api.get("/payments/config").then((res) => setPayConfig(res.data));
    }
  }, []);

  const handleSearch = (e) => {
    e.preventDefault();
    setBooking(null);
    setSearched(true);
    loadTrains();
  };

  const startBooking = (train, trainClass, waitlist = false) => {
    // if the search named two stations on this train's route, book that part only
    const onRoute = (station) =>
      train.stops?.some((s) => s.station.toLowerCase() === station.trim().toLowerCase());
    const segment = source.trim() && destination.trim() && onRoute(source) && onRoute(destination);
    setBooking({
      train,
      trainClass,
      waitlist,
      from: segment ? source.trim() : null,
      to: segment ? destination.trim() : null,
    });
    setPassengers([{ ...emptyPassenger }]);
  };

  const updatePassenger = (index, field, value) => {
    setPassengers(passengers.map((p, i) => (i === index ? { ...p, [field]: value } : p)));
  };

  const addPassenger = () => {
    if (passengers.length < MAX_PASSENGERS) {
      setPassengers([...passengers, { ...emptyPassenger }]);
    }
  };

  const removePassenger = (index) => {
    setPassengers(passengers.filter((_, i) => i !== index));
  };

  const confirmBooking = async (e) => {
    e.preventDefault();
    try {
      const res = await api.post("/bookings", {
        trainId: booking.train.id,
        seatClass: booking.trainClass.seatClass,
        journeyDate: date,
        passengers: passengers.map((p) => ({ name: p.name, age: Number(p.age), gender: p.gender })),
        allowWaitlist: booking.waitlist,
        fromStation: booking.from,
        toStation: booking.to,
      });
      setBooking(null);
      if (res.data.status === "PENDING") {
        payForBooking(res.data, payConfig.keyId, user, (result) => {
          result.type === "success" ? toast.success(result.text) : toast.error(result.text);
          loadTrains();
        });
      } else {
        const details = res.data.passengers
          .map((p) => `${p.name}: ${p.status === "WAITLISTED" ? `WL${p.waitlistNumber}` : `Seat ${p.seatNumber}`}`)
          .join(", ");
        toast.success(
          `Booked! PNR ${res.data.pnr} — ${res.data.seatClassLabel} on ${res.data.trainName}. ${details}. Total ₹${res.data.totalFare}`,
          { autoClose: 9000 }
        );
      }
      loadTrains();
    } catch (err) {
      toast.error(err.response?.data?.message || "Booking failed");
    }
  };

  // fare for the searched part of the route (equals the full fare when not a segment)
  const fareOf = (trainClass) => trainClass.segmentFare || trainClass.fare;
  const isPartJourney = (trainClass) => fareOf(trainClass) !== trainClass.fare;

  return (
    <div>
      <section className="hero">
        <div className="hero-inner">
          <h2>Search Trains</h2>
          <p className="hero-sub">
            Book by any two stations on a train's route — you pay only for the distance you travel.
          </p>
          <form className="search-bar" onSubmit={handleSearch}>
            <div className="field">
              <label htmlFor="from">From</label>
              <input id="from" placeholder="e.g. Pune" value={source} onChange={(e) => setSource(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="to">To</label>
              <input id="to" placeholder="e.g. Mumbai" value={destination} onChange={(e) => setDestination(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="date">Journey date</label>
              <input id="date" type="date" value={date} min={today} onChange={(e) => setDate(e.target.value)} />
            </div>
            <div className="field" style={{ flex: "0 0 auto", justifyContent: "flex-end" }}>
              <button type="submit" className="btn"><FaSearch /> Search</button>
            </div>
          </form>
        </div>
      </section>

      {booking && (
        <form className="card" onSubmit={confirmBooking}>
          <h3>
            Passenger details — {booking.train.name}{" "}
            ({booking.from || booking.train.source} → {booking.to || booking.train.destination})
            <span className="class-badge">{booking.trainClass.seatClassLabel}</span> on {date}
          </h3>
          {booking.from && (
            <p className="hint">
              Part of the route {booking.train.source} → {booking.train.destination}; charged for
              {" "}{booking.from} → {booking.to} only.
            </p>
          )}
          {booking.waitlist && (
            <p className="alert alert-warn" style={{ marginTop: 12 }}>
              This class is full. Your ticket will be <strong>waitlisted</strong> and confirmed
              automatically if someone cancels. The fare is charged now and refunded in full if you cancel.
            </p>
          )}
          <div style={{ marginTop: 12 }}>
            {passengers.map((p, i) => (
              <div className="passenger-row" key={i}>
                <input
                  placeholder={`Passenger ${i + 1} name`}
                  value={p.name}
                  onChange={(e) => updatePassenger(i, "name", e.target.value)}
                  required
                />
                <input
                  type="number"
                  placeholder="Age"
                  min="1"
                  max="120"
                  value={p.age}
                  onChange={(e) => updatePassenger(i, "age", e.target.value)}
                  required
                />
                <select value={p.gender} onChange={(e) => updatePassenger(i, "gender", e.target.value)}>
                  <option>Male</option>
                  <option>Female</option>
                  <option>Other</option>
                </select>
                {passengers.length > 1 && (
                  <button type="button" className="btn btn-small btn-danger" onClick={() => removePassenger(i)}>
                    <FaTimes />
                  </button>
                )}
              </div>
            ))}
          </div>
          <div className="form-actions" style={{ marginTop: 12 }}>
            <button
              type="button"
              className="btn btn-small"
              onClick={addPassenger}
              disabled={passengers.length >= MAX_PASSENGERS}
            >
              <FaPlus /> Add Passenger
            </button>
            <span className="fare-preview">
              Total fare: ₹{passengers.length * fareOf(booking.trainClass)}
            </span>
            <button type="submit" className="btn">Confirm Booking</button>
            <button type="button" className="btn btn-small" onClick={() => setBooking(null)}>Close</button>
          </div>
        </form>
      )}

      {loading ? (
        <div className="state">
          <div className="spinner" />
          Loading trains…
        </div>
      ) : trains.length === 0 ? (
        <div className="state">
          <div className="state-icon"><FaTrain /></div>
          <div className="state-title">No trains found</div>
          {searched
            ? "Try different stations, or check the spelling of the station names."
            : "No trains are available right now."}
        </div>
      ) : (
        trains.map((train) => (
          <div className="card train-card" key={train.id}>
            <div className="train-head">
              <div>
                <div className="train-title">{train.name}</div>
                <div className="muted">#{train.trainNumber} · {train.source} → {train.destination}</div>
                {train.runsOn?.length > 0 && train.runsOn.length < 7 && (
                  <div className="muted">Runs on: {formatRunDays(train.runsOn)}</div>
                )}
              </div>
              <div className="train-timing">
                {train.departureTime} – {train.arrivalTime}
              </div>
            </div>

            <div className="train-body">
              {train.stops?.length > 0 && (
                <div className="route">
                  {train.stops.map((s, i) => (
                    <div
                      className={`route-stop${i === 0 || i === train.stops.length - 1 ? " is-end" : ""}`}
                      key={s.id}
                    >
                      <div className="route-dot" />
                      <div className="route-station">{s.station}</div>
                      <div className="route-time">{s.departureTime || s.arrivalTime}</div>
                    </div>
                  ))}
                </div>
              )}

              <table className="table class-table">
                <thead>
                  <tr>
                    <th>Class</th>
                    <th className="num">Fare</th>
                    <th>Availability</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {train.classes.map((c) => (
                    <tr key={c.id}>
                      <td data-label="Class"><span className="class-badge">{c.seatClassLabel}</span></td>
                      <td data-label="Fare" className="num">
                        <div className="fare-main">₹{fareOf(c)}</div>
                        {isPartJourney(c) && <div className="fare-note">full route ₹{c.fare}</div>}
                      </td>
                      <td data-label="Availability">
                        <span className={c.availableSeats > 0 ? "seats-ok" : "seats-full"}>
                          {c.availableSeats > 0
                            ? `${c.availableSeats} seats`
                            : c.waitlistCount >= MAX_WAITLIST
                            ? "Regret / WL full"
                            : `Full — WL ${c.waitlistCount}`}
                        </span>
                      </td>
                      <td>
                        {!user ? (
                          <Link to="/login">Login to book</Link>
                        ) : c.availableSeats > 0 ? (
                          <button className="btn btn-small" onClick={() => startBooking(train, c)}>
                            Book
                          </button>
                        ) : c.waitlistCount < MAX_WAITLIST ? (
                          <button className="btn btn-small btn-warn" onClick={() => startBooking(train, c, true)}>
                            Join Waitlist
                          </button>
                        ) : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
