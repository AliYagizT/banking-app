// Public configuration. The Firebase web config is NOT a secret (it only identifies the
// project to Firebase); the real secret is the backend service-account key, which never
// ships to the browser.

export const firebaseConfig = {
  apiKey: "AIzaSyCgWiJA4-t6CjfEkjsL2ibQm3lSHMcjsJI",
  authDomain: "banking-softtech.firebaseapp.com",
  projectId: "banking-softtech",
  storageBucket: "banking-softtech.firebasestorage.app",
  messagingSenderId: "373832614205",
  appId: "1:373832614205:web:b307a579886fbca7b14554",
};

// Base URL of the Spring backend. The app runs on 8081 in this environment (8080 was
// taken); change this if you run the backend elsewhere.
export const API_BASE_URL = "http://localhost:8081";
