import { legacy_createStore as createStore } from "redux";

// ---- action types ----
const LOGIN = "auth/LOGIN";
const LOGOUT = "auth/LOGOUT";
const UPDATE_USER = "auth/UPDATE_USER";

// ---- action creators ----
export const loginAction = (user) => ({ type: LOGIN, payload: user });
export const logoutAction = () => ({ type: LOGOUT });
// merges changed fields (e.g. name) into the logged-in user
export const updateUserAction = (changes) => ({ type: UPDATE_USER, payload: changes });

// user saved in localStorage so login survives a page refresh
const savedUser = localStorage.getItem("user");
const initialState = {
  user: savedUser ? JSON.parse(savedUser) : null,
};

// ---- reducer (pure function: state + action -> new state) ----
function authReducer(state = initialState, action) {
  switch (action.type) {
    case LOGIN:
      return { ...state, user: action.payload };
    case LOGOUT:
      return { ...state, user: null };
    case UPDATE_USER:
      return { ...state, user: { ...state.user, ...action.payload } };
    default:
      return state;
  }
}

// ---- store ----
export const store = createStore(authReducer);
