import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { Provider } from "react-redux";
import { ToastContainer } from "react-toastify";
import { store } from "./store";
import App from "./App";
import "react-toastify/dist/ReactToastify.css";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  
  <React.StrictMode>
    <Provider store={store}>
      <BrowserRouter>
        <App />
        {/* one place for all success/error pop-ups */}
        <ToastContainer position="top-right" autoClose={4000} newestOnTop theme="colored" />
      </BrowserRouter>
    </Provider>
  </React.StrictMode>
);
