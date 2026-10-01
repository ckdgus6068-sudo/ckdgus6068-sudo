// The Firebase web app this page talks to, copied from the Firebase console
// (Project settings → Your apps → Web app → firebaseConfig).
// These values are meant to be public: who may read or write what is decided by
// jelly-calendar/share/firestore.rules, not by keeping this file secret.
export const firebaseConfig = {
  apiKey: 'AIzaSyAjOfndmEyJwOlhaxrTwoSFpSkciv5SG-Q',
  authDomain: 'jelly-share-4b244.firebaseapp.com',
  projectId: 'jelly-share-4b244',
  storageBucket: 'jelly-share-4b244.firebasestorage.app',
  messagingSenderId: '593175955145',
  appId: '1:593175955145:web:392ac6365fc8785daf6f18',
};

// Where people open the shared calendar in a browser; invite links point here, also when the page
// runs inside the Android app (which carries its own copy of it).
export const publicUrl = 'https://ckdgus6068-sudo.github.io/ckdgus6068-sudo/share/';

// Whether to offer "구글로 시작하기" in a browser. Turn on once Google sign-in is enabled in the
// Firebase console (Authentication → Sign-in method → Google) and this site's domain is listed
// under Authentication → Settings → Authorized domains. Inside the Android app the app decides.
export const googleSignIn = true;

