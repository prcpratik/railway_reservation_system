import { useEffect, useState } from "react";
import { toast } from "react-toastify";
import { FaPlus, FaTimes } from "react-icons/fa";
import api from "../api";

// values must match the SeatClass enum on the backend
const SEAT_CLASSES = [
  { value: "AC1", label: "1AC" },
  { value: "AC2", label: "2AC" },
  { value: "AC3", label: "3AC" },
  { value: "SLEEPER", label: "Sleeper" },
  { value: "GENERAL", label: "General" },
];

const emptyClass = { seatClass: "AC3", totalSeats: 50, fare: 500 };
const emptyStop = { station: "", arrivalTime: "", departureTime: "", distanceFromSource: 0 };

// "MONDAY, WEDNESDAY, FRIDAY" -> "Mon, Wed, Fri" (or "Daily" when all seven are set)
const DAY_ORDER = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const DAY_SHORT = { MONDAY: "Mon", TUESDAY: "Tue", WEDNESDAY: "Wed", THURSDAY: "Thu", FRIDAY: "Fri", SATURDAY: "Sat", SUNDAY: "Sun" };
const formatRunDays = (days) => {
  if (!days || days.length === 0) return "—";
  if (days.length === 7) return "Daily";
  return DAY_ORDER.filter((d) => days.includes(d)).map((d) => DAY_SHORT[d]).join(", ");
};

// must match java.time.DayOfWeek names, which is what the backend sends and expects
const WEEKDAYS = [
  { value: "MONDAY", label: "Mon" },
  { value: "TUESDAY", label: "Tue" },
  { value: "WEDNESDAY", label: "Wed" },
  { value: "THURSDAY", label: "Thu" },
  { value: "FRIDAY", label: "Fri" },
  { value: "SATURDAY", label: "Sat" },
  { value: "SUNDAY", label: "Sun" },
];
const ALL_DAYS = WEEKDAYS.map((d) => d.value);
const WEEKDAY_DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];
const WEEKEND_DAYS = ["SATURDAY", "SUNDAY"];

const emptyForm = {
  trainNumber: "",
  name: "",
  source: "",
  destination: "",
  departureTime: "",
  arrivalTime: "",
  classes: [{ ...emptyClass }],
  stops: [],
  runsOn: [...ALL_DAYS], // default: runs every day
};

export default function AdminTrains() {
  const [trains, setTrains] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);

  const loadTrains = async () => {
    const res = await api.get("/trains");
    setTrains(res.data);
  };

  useEffect(() => {
    loadTrains();
  }, []);

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const updateClass = (index, field, value) => {
    setForm({
      ...form,
      classes: form.classes.map((c, i) => (i === index ? { ...c, [field]: value } : c)),
    });
  };

  const addClassRow = () => {
    // offer a class that is not used yet
    const used = form.classes.map((c) => c.seatClass);
    const next = SEAT_CLASSES.find((c) => !used.includes(c.value));
    if (!next) return; // all five already added
    setForm({ ...form, classes: [...form.classes, { ...emptyClass, seatClass: next.value }] });
  };

  const removeClassRow = (index) => {
    setForm({ ...form, classes: form.classes.filter((_, i) => i !== index) });
  };

  const updateStop = (index, field, value) => {
    setForm({
      ...form,
      stops: form.stops.map((s, i) => (i === index ? { ...s, [field]: value } : s)),
    });
  };

  const addStopRow = () => {
    setForm({ ...form, stops: [...form.stops, { ...emptyStop }] });
  };

  const removeStopRow = (index) => {
    setForm({ ...form, stops: form.stops.filter((_, i) => i !== index) });
  };

  const toggleDay = (day) => {
    const next = form.runsOn.includes(day)
      ? form.runsOn.filter((d) => d !== day)
      : [...form.runsOn, day];
    setForm({ ...form, runsOn: next });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const payload = {
      ...form,
      classes: form.classes.map((c) => ({
        seatClass: c.seatClass,
        totalSeats: Number(c.totalSeats),
        fare: Number(c.fare),
      })),
      // route is optional; send only rows that actually have a station
      stops: form.stops
        .filter((s) => s.station.trim() !== "")
        .map((s) => ({ ...s, distanceFromSource: Number(s.distanceFromSource) || 0 })),
      runsOn: form.runsOn,
    };
    if (payload.runsOn.length === 0) {
      toast.error("Pick at least one day the train runs on");
      return;
    }
    try {
      if (editingId) {
        await api.put(`/trains/${editingId}`, payload);
        toast.success("Train updated");
      } else {
        await api.post("/trains", payload);
        toast.success("Train added");
      }
      setForm(emptyForm);
      setEditingId(null);
      loadTrains();
    } catch (err) {
      toast.error(err.response?.data?.message || "Save failed");
    }
  };

  const handleEdit = (train) => {
    setEditingId(train.id);
    setForm({
      trainNumber: train.trainNumber,
      name: train.name,
      source: train.source,
      destination: train.destination,
      departureTime: train.departureTime,
      arrivalTime: train.arrivalTime,
      classes: train.classes.map((c) => ({
        seatClass: c.seatClass,
        totalSeats: c.totalSeats,
        fare: c.fare,
      })),
      stops: (train.stops || []).map((s) => ({
        station: s.station,
        arrivalTime: s.arrivalTime || "",
        departureTime: s.departureTime || "",
        distanceFromSource: s.distanceFromSource ?? 0,
      })),
      // backend returns runsOn as an array of DayOfWeek strings; default to daily
      runsOn: train.runsOn?.length > 0 ? [...train.runsOn] : [...ALL_DAYS],
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this train?")) return;
    try {
      await api.delete(`/trains/${id}`);
      loadTrains();
    } catch (err) {
      toast.error("Delete failed - the train may have bookings");
    }
  };

  return (
    <div>
      <h2>Manage Trains (Admin)</h2>

      <form className="card admin-form" onSubmit={handleSubmit}>
        <h3>{editingId ? "Edit Train" : "Add New Train"}</h3>
        <div className="form-grid">
          <input name="trainNumber" placeholder="Train Number" value={form.trainNumber} onChange={handleChange} required />
          <input name="name" placeholder="Train Name" value={form.name} onChange={handleChange} required />
          <input name="source" placeholder="Source" value={form.source} onChange={handleChange} required />
          <input name="destination" placeholder="Destination" value={form.destination} onChange={handleChange} required />
          <input name="departureTime" placeholder="Departure (e.g. 08:30)" value={form.departureTime} onChange={handleChange} required />
          <input name="arrivalTime" placeholder="Arrival (e.g. 12:45)" value={form.arrivalTime} onChange={handleChange} required />
        </div>

        <h4 className="class-heading">Runs On</h4>
        <p className="hint">Tick the days this train runs. It will only appear in search results for those days.</p>
        <div className="day-picker">
          {WEEKDAYS.map((d) => (
            <label
              key={d.value}
              className={`day-chip${form.runsOn.includes(d.value) ? " on" : ""}`}
            >
              <input
                type="checkbox"
                checked={form.runsOn.includes(d.value)}
                onChange={() => toggleDay(d.value)}
              />
              {d.label}
            </label>
          ))}
          <span className="day-quicks">
            <button type="button" className="btn btn-small" onClick={() => setForm({ ...form, runsOn: [...ALL_DAYS] })}>Daily</button>
            <button type="button" className="btn btn-small" onClick={() => setForm({ ...form, runsOn: [...WEEKDAY_DAYS] })}>Weekdays</button>
            <button type="button" className="btn btn-small" onClick={() => setForm({ ...form, runsOn: [...WEEKEND_DAYS] })}>Weekends</button>
          </span>
        </div>

        <h4 className="class-heading">Travel Classes</h4>
        <p className="hint">Add only the classes this train actually runs, with seats and fare for each.</p>
        {form.classes.map((c, i) => (
          <div className="class-row" key={i}>
            <select value={c.seatClass} onChange={(e) => updateClass(i, "seatClass", e.target.value)}>
              {SEAT_CLASSES.map((sc) => (
                <option key={sc.value} value={sc.value}>{sc.label}</option>
              ))}
            </select>
            <input
              type="number"
              min="1"
              placeholder="Total seats"
              value={c.totalSeats}
              onChange={(e) => updateClass(i, "totalSeats", e.target.value)}
              required
            />
            <input
              type="number"
              min="0"
              placeholder="Fare per seat"
              value={c.fare}
              onChange={(e) => updateClass(i, "fare", e.target.value)}
              required
            />
            {form.classes.length > 1 && (
              <button type="button" className="btn btn-small btn-danger" onClick={() => removeClassRow(i)}>✕</button>
            )}
          </div>
        ))}

        <h4 className="class-heading">Route / Stops <span className="muted">(optional)</span></h4>
        <p className="hint">
          List the stations in travel order. When a route is given, the train's source,
          destination and timings are taken from the first and last stop, and passengers
          can search using any two stations on the route. The <strong>km from start</strong>
          values are used to charge part-journeys in proportion to distance travelled
          (start at 0 and increase along the route).
        </p>
        {form.stops.map((s, i) => (
          <div className="class-row" key={i}>
            <span className="stop-index">{i + 1}</span>
            <input
              placeholder="Station"
              value={s.station}
              onChange={(e) => updateStop(i, "station", e.target.value)}
            />
            <input
              placeholder={i === 0 ? "— (start)" : "Arrival e.g. 16:00"}
              value={s.arrivalTime}
              onChange={(e) => updateStop(i, "arrivalTime", e.target.value)}
              disabled={i === 0}
            />
            <input
              placeholder={i === form.stops.length - 1 ? "— (end)" : "Departure e.g. 16:10"}
              value={s.departureTime}
              onChange={(e) => updateStop(i, "departureTime", e.target.value)}
              disabled={i === form.stops.length - 1}
            />
            <input
              type="number"
              min="0"
              placeholder="km from start"
              value={s.distanceFromSource}
              onChange={(e) => updateStop(i, "distanceFromSource", e.target.value)}
            />
            <button type="button" className="btn btn-small btn-danger" onClick={() => removeStopRow(i)}>✕</button>
          </div>
        ))}

        <div className="form-actions">
          <button type="button" className="btn btn-small" onClick={addStopRow}>+ Add Stop</button>
          <button
            type="button"
            className="btn btn-small"
            onClick={addClassRow}
            disabled={form.classes.length >= SEAT_CLASSES.length}
          >
            + Add Class
          </button>
          <button type="submit" className="btn">{editingId ? "Update" : "Add"} Train</button>
          {editingId && (
            <button type="button" className="btn btn-small" onClick={() => { setEditingId(null); setForm(emptyForm); }}>
              Cancel Edit
            </button>
          )}
        </div>
      </form>

      <table className="table">
        <thead>
          <tr>
            <th>Number</th>
            <th>Name</th>
            <th>Route</th>
            <th>Timing</th>
            <th>Runs On</th>
            <th>Classes (seats @ fare)</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {trains.map((train) => (
            <tr key={train.id}>
              <td data-label="Number">{train.trainNumber}</td>
              <td data-label="Name">{train.name}</td>
              <td>
                {train.source} → {train.destination}
                {train.stops?.length > 0 && (
                  <div className="muted">
                    via {train.stops.slice(1, -1).map((s) => s.station).join(", ") || "direct"}
                  </div>
                )}
              </td>
              <td data-label="Timing">{train.departureTime} – {train.arrivalTime}</td>
              <td data-label="Runs On">{formatRunDays(train.runsOn)}</td>
              <td>
                {train.classes.map((c) => (
                  <div key={c.id} className="muted">
                    <span className="class-badge">{c.seatClassLabel}</span> {c.totalSeats} @ ₹{c.fare}
                  </div>
                ))}
              </td>
              <td>
                <button className="btn btn-small" onClick={() => handleEdit(train)}>Edit</button>{" "}
                <button className="btn btn-small btn-danger" onClick={() => handleDelete(train.id)}>Delete</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
