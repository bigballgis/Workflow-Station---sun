import os

# ==============================================================================
# Database
# ==============================================================================
SQLALCHEMY_DATABASE_URI = os.getenv(
    "SQLALCHEMY_DATABASE_URI",
    "postgresql+psycopg2://platform_dev:dev_password_123@host.docker.internal:5432/workflow_platform_dev?options=-csearch_path%3Dsuperset",
)

SUPERSET_SECRET_KEY = os.getenv(
    "SUPERSET_SECRET_KEY",
    "replace_this_with_a_real_secert_key"
)

# ==============================================================================
# Feature Flags
# ==============================================================================
FEATURE_FLAGS = {
    "EMBEDDED_SUPERSET": True,
    "ALERTS": True,
}

# ==============================================================================
# Guest Token (Embedded Dashboard)
# ==============================================================================
GUEST_ROLE_NAME = "Gamma"
GUEST_TOKEN_JWT_SECRET = os.getenv(
    "SUPERSET_SECRET_KEY",
    "replace_this_with_a_real_secert_key"
)
GUEST_TOKEN_JWT_ALGO = "HS256"
GUEST_TOKEN_HEADER_NAME = "X-GuestToken"
GUEST_TOKEN_JWT_EXP_SECONDS = 300

# ==============================================================================
# CORS — allow embedded iframe to call Superset APIs
# ==============================================================================
ENABLE_CORS = True
CORS_OPTIONS = {
    "supports_credentials": True,
    "allow_headers": ["*"],
    "resources": ["/api/*", "/superset/csrf_token/", "/guest_token/"],
    "origins": [
        "http://localhost:3000",
        "http://localhost:3001",
        "http://localhost:8088",
    ],
}

# ==============================================================================
# Security — disable Talisman & relax CSP for iframe embedding
# ==============================================================================
TALISMAN_ENABLED = False
CONTENT_SECURITY_POLICY_WARNING = False

# Allow Superset to be embedded in iframes
HTTP_HEADERS = {
    "X-Frame-Options": "ALLOWALL",
}

# Exempt the guest_token API from WTF CSRF so the backend can call it
# with just the X-CSRFToken header + session cookie (no form-based token)
WTF_CSRF_EXEMPT_LIST = [
    "superset.security.api",
]
