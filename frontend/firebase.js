// Firebase Authentication wrapper. Initializes the SDK and exposes the few auth
// operations the app needs (email/password sign-in and sign-up, sign-out, the current
// user's ID token, and an auth-state subscription). The ID token is what the backend
// verifies on every API call.

import { initializeApp } from "https://www.gstatic.com/firebasejs/12.16.0/firebase-app.js";
import {
  getAuth,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut as fbSignOut,
  onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-auth.js";
import { firebaseConfig } from "./config.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);

export function onAuth(callback) {
  return onAuthStateChanged(auth, callback);
}

export function signIn(email, password) {
  return signInWithEmailAndPassword(auth, email, password);
}

export function signUp(email, password) {
  return createUserWithEmailAndPassword(auth, email, password);
}

export function signOut() {
  return fbSignOut(auth);
}

// Fresh ID token for the current user (auto-refreshed by the SDK).
export async function idToken() {
  const user = auth.currentUser;
  if (!user) return null;
  return user.getIdToken();
}

// Map Firebase auth error codes to friendly Turkish messages.
export function friendlyAuthError(code) {
  const map = {
    "auth/invalid-email": "Geçersiz e-posta adresi.",
    "auth/user-disabled": "Bu hesap devre dışı bırakılmış.",
    "auth/user-not-found": "E-posta veya şifre hatalı.",
    "auth/wrong-password": "E-posta veya şifre hatalı.",
    "auth/invalid-credential": "E-posta veya şifre hatalı.",
    "auth/email-already-in-use": "Bu e-posta zaten kayıtlı.",
    "auth/weak-password": "Şifre en az 6 karakter olmalı.",
    "auth/too-many-requests": "Çok fazla deneme. Lütfen biraz bekleyin.",
    "auth/network-request-failed": "Ağ hatası. Bağlantınızı kontrol edin.",
  };
  return map[code] || "Bir kimlik doğrulama hatası oluştu.";
}
