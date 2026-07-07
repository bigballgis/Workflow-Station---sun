import os

from flask_appbuilder.security.manager import AUTH_REMOTE_USER

from superset_security_manager import PlatformRemoteUserSecurityManager

# ==============================================================================
# Database
# ==============================================================================
SQLALCHEMY_DATABASE_URI = os.getenv(
    "SQLALCHEMY_DATABASE_URI",
    "postgresql+psycopg2://platform_dev:dev_password_123@host.docker.internal:5432/workflow_platform_dev?options=-csearch_path%3Dsuperset",
)

# ==============================================================================
# Secret key
# ------------------------------------------------------------------------------
# Must be supplied via env (compose .env / k8s Secret). No insecure literal
# fallback: a wrong/weak SECRET_KEY lets anyone forge guest tokens. Fail closed
# if it is missing rather than silently running with a default.
# (Superset's own default config also derives SECRET_KEY from SUPERSET_SECRET_KEY;
#  we set it explicitly so the source of truth is unambiguous.)
# ==============================================================================
SECRET_KEY = os.environ["SUPERSET_SECRET_KEY"]

# ==============================================================================
# Subpath hosting (single-FQDN + path routing)
# ------------------------------------------------------------------------------
# Superset 6.0 NATIVELY reads the SUPERSET_APP_ROOT env var (see superset/app.py)
# and sets APPLICATION_ROOT from it, mounting the whole app under that prefix —
# routes, static-asset URLs, and redirects all carry it. So to serve Superset at
# e.g. http://<host>/bi (same FQDN as /admin, /portal, /dev) we ONLY set that env
# var (compose/k8s); the edge forwards /bi/* unchanged (no prefix stripping).
# Here we merely enable ProxyFix so Superset honors the edge's X-Forwarded-Proto/
# Host when building absolute URLs (correct https scheme for guest-token/embedded
# links in prod). Do NOT set STATIC_ASSETS_PREFIX: APPLICATION_ROOT already
# prefixes assets, so adding it would double the prefix (/bi/bi/static).
# ==============================================================================
if os.getenv("SUPERSET_APP_ROOT", "").rstrip("/"):
    ENABLE_PROXY_FIX = True

# ==============================================================================
# Feature Flags
# ==============================================================================
FEATURE_FLAGS = {
    "EMBEDDED_SUPERSET": True,
    "ALERTS": True,
}

# ==============================================================================
# Native-UI authentication — platform SSO via trusted edge-gateway headers.
# ------------------------------------------------------------------------------
# Authors/admins opening the Superset UI (/superset/) are authenticated by the
# edge gateway (validates platform JWT, injects X-Remote-User / X-Remote-Roles,
# strips client-forged headers). See superset_security_manager.py.
# The embedded guest-token viewer path is unaffected (no login session).
# ==============================================================================
AUTH_TYPE = AUTH_REMOTE_USER
AUTH_REMOTE_USER_ENV_VAR = "HTTP_X_REMOTE_USER"
AUTH_USER_REGISTRATION = True
AUTH_USER_REGISTRATION_ROLE = "Public"  # minimal until roles are mapped by the gateway
CUSTOM_SECURITY_MANAGER = PlatformRemoteUserSecurityManager

# ==============================================================================
# Guest Token (Embedded Dashboard)
# ------------------------------------------------------------------------------
# Defaults to SECRET_KEY (Superset's own default). Override with a dedicated
# secret only if you want to rotate it independently.
# ==============================================================================
GUEST_ROLE_NAME = os.getenv("SUPERSET_GUEST_ROLE_NAME", "Gamma")
GUEST_TOKEN_JWT_SECRET = os.getenv("SUPERSET_GUEST_TOKEN_JWT_SECRET") or SECRET_KEY
GUEST_TOKEN_JWT_ALGO = "HS256"
GUEST_TOKEN_HEADER_NAME = "X-GuestToken"
GUEST_TOKEN_JWT_EXP_SECONDS = 300

# Superset 6.0's common_bootstrap_payload (used by the embedded-dashboard view)
# unconditionally reads these keys; this minimal config must define them or the
# embedded render returns HTTP 500 (KeyError: 'RECAPTCHA_PUBLIC_KEY').
RECAPTCHA_PUBLIC_KEY = os.getenv("SUPERSET_RECAPTCHA_PUBLIC_KEY", "")
RECAPTCHA_PRIVATE_KEY = os.getenv("SUPERSET_RECAPTCHA_PRIVATE_KEY", "")

# Logout under gateway/REMOTE_USER SSO: Superset's own logout can't end the session
# (the gate re-authenticates from the still-valid platform JWT cookie). Redirect the
# Logout button to the platform endpoint that clears that cookie, then lands on login.
LOGOUT_REDIRECT_URL = os.getenv(
    "SUPERSET_LOGOUT_REDIRECT_URL",
    "http://localhost:3000/api/v1/admin/auth/logout-redirect",
)

# ==============================================================================
# CORS — restrict to the portal origin(s) that embed dashboards.
# Was origins=["*"] (any site could call Superset APIs with credentials).
# ==============================================================================
_cors_origins = [
    o.strip()
    for o in os.getenv("SUPERSET_CORS_ORIGINS", "http://localhost:3000").split(",")
    if o.strip()
]
ENABLE_CORS = True
CORS_OPTIONS = {
    "supports_credentials": True,
    "allow_headers": ["*"],
    "resources": ["/api/*", "/superset/csrf_token/", "/guest_token/"],
    "origins": _cors_origins,
}

# ==============================================================================
# Framing — allow only the portal origin(s) to iframe Superset.
# Was X-Frame-Options: ALLOWALL (any site could frame it -> clickjacking).
# CSP frame-ancestors is the modern allowlist (X-Frame-Options can't express one).
# ==============================================================================
TALISMAN_ENABLED = False
CONTENT_SECURITY_POLICY_WARNING = False
_frame_ancestors = " ".join(["'self'"] + _cors_origins)
HTTP_HEADERS = {
    "Content-Security-Policy": f"frame-ancestors {_frame_ancestors};",
}

# Exempt the guest_token API from WTF CSRF so the backend can call it
# with just the X-CSRFToken header + session cookie (no form-based token)
WTF_CSRF_EXEMPT_LIST = [
    "superset.security.api",
]
