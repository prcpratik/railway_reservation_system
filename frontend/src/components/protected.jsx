// ProtectedRoute.jsx
import { Navigate } from "react-router-dom";

const ProtectedRoute = ({ children }) => {
  const isAuthenticated = localStorage.getItem("token");

  return isAuthenticated ? children : <Navigate to="/login" replace />;
};

export default ProtectedRoute;

//comment from komal will remove afterwards


//comment from Mitali will be removed later

