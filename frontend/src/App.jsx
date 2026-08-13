import { Routes, Route, Navigate } from "react-router-dom";
import { useSelector } from "react-redux";
import Navbar from "./components/Navbar";
import Trains from "./pages/Trains";
import Login from "./pages/Login";
import Register from "./pages/Register";
import MyBookings from "./pages/MyBookings";
import AdminTrains from "./pages/AdminTrains";
import Profile from "./pages/Profile";
import About from "./pages/About";
import AllBookings from "./pages/AllBookings";
import CreateAdmin from "./pages/CreateAdmin";
import ForgotPassword from "./pages/ForgotPassword";
import ResetPassword from "./pages/ResetPassword";
import Chatbot from "./components/Chatbot"; // 1. Import Chatbot component


export default function App() {
  const user = useSelector((state) => state.user);

  return (
    <>
      <Navbar />
      <main className="container">
        <Routes>
          <Route
            path="/"
            element={user?.role === "ADMIN" ? <Navigate to="/admin" /> : <Trains />}
          />
          <Route path="/about" element={<About />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route
            path="/my-bookings"
            element={user ? <MyBookings /> : <Navigate to="/login" />}
          />
          <Route
            path="/profile"
            element={user ? <Profile /> : <Navigate to="/login" />}
          />
          <Route
            path="/admin"
            element={user?.role === "ADMIN" ? <AdminTrains /> : <Navigate to="/login" />}
          />
          <Route
            path="/all-bookings"
            element={user?.role === "ADMIN" ? <AllBookings /> : <Navigate to="/login" />}
          />
          <Route
            path="/create-admin"
            element={user?.role === "ADMIN" ? <CreateAdmin /> : <Navigate to="/login" />}
          />
        </Routes>
      </main>
      <Chatbot /> {/* 2. Include Chatbot component in the layout */}
    </>
  );
}
