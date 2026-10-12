window.__ENV__ = {
  VITE_API_BASE_URL: "http://localhost:8002",
  MODE: "GLOBAL",
  APPLICATION: "MAIL",
  SSO_BASE_URL: "http://sso.example.com",
  SSO_CLIENT_ID: "twake-mail-admin",
  SSO_SCOPE: "openid profile email",
  SSO_REDIRECT_URI: "http://localhost:3333/oidc-callback",
  SSO_RESPONSE_TYPE: "code",
  SSO_CODE_CHALLENGE_METHOD: "S256",
  SSO_POST_LOGOUT_REDIRECT: "http://localhost:3333?logout=1",
  SSO_ALLOW_INSECURE_REQUESTS: "true",
};
