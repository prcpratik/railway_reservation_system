import { Link, useNavigate } from "react-router-dom";
import { useSelector, useDispatch } from "react-redux";
import {
  FaTrain,
  FaTicketAlt,
  FaUserCircle,
  FaSignOutAlt,
  FaSignInAlt,
  FaUserPlus,
  FaInfoCircle,
  FaClipboardList,
  FaUserShield,
} from "react-icons/fa";
import { logoutAction } from "../store";

export default function Navbar() {
  const user = useSelector((state) => state.user);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const isAdmin = user?.role === "ADMIN";

  const handleLogout = () => {
    localStorage.removeItem("user");
    dispatch(logoutAction());
    navigate("/");
  };

  return (
    <nav className="navbar">
      <Link to={isAdmin ? "/admin" : "/"} className="brand">
        <FaTrain /> RailSathi
      </Link>
      <div className="nav-links">
        {isAdmin ? (
          <>
            <Link to="/admin"><FaTrain /> Manage Trains</Link>
            <Link to="/all-bookings"><FaClipboardList /> All Bookings</Link>
            <Link to="/create-admin"><FaUserShield /> Add Admin</Link>
          </>
        ) : (
          <>
            <Link to="/"><FaTrain /> Trains</Link>
            {user && <Link to="/my-bookings"><FaTicketAlt /> My Bookings</Link>}
          </>
        )}
        <Link to="/about"><FaInfoCircle /> About</Link>
        {user ? (
          <>
            <Link to="/profile" className="nav-user"><FaUserCircle /> Hi, {user.name}</Link>
            <button className="btn btn-small" onClick={handleLogout}>
              <FaSignOutAlt /> Logout
            </button>
          </>
        ) : (
          <>
            <Link to="/login"><FaSignInAlt /> Login</Link>
            <Link to="/register"><FaUserPlus /> Register</Link>
          </>
        )}
      </div>
    </nav>
  );
}
