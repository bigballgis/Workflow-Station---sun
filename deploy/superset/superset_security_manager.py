"""Custom Superset SecurityManager: authenticate Superset's native UI via the
platform's unified SSO, using trusted headers injected by the edge gateway.

Flow (author / admin who opens the Superset UI):
  1. Edge gateway validates the platform JWT (auth_request -> admin-center).
  2. Gateway maps the user's platform roles to Superset role names via
     ac_bi_rbac_mappings and injects:
        X-Remote-User      -> login name (REQUIRED)
        X-Remote-Roles     -> comma-separated Superset role names (e.g. "Gamma,Alpha")
        X-Remote-Email     -> optional
        X-Remote-Firstname -> optional
        X-Remote-Lastname  -> optional
  3. The gateway STRIPS any client-supplied X-Remote-* headers, so these are
     trusted. Superset must never be reachable bypassing the gateway.

Why this class is required (verified against Superset 6.0 / FAB 5.0):
  - Superset 6.0 hard-codes SupersetAuthView for /login regardless of AUTH_TYPE,
    and that view only renders a template (no REMOTE_USER auth). We register a
    SupersetAuthView subclass that performs REMOTE_USER auth at /login while
    keeping ALL of Superset's other register_views() behavior (notably the
    removal of the legacy FAB user/role/group CRUD views + menu items — skipping
    that makes "List Users" reappear and render blank).
  - FAB's default auth_user_remote_user() only assigns a role on first creation
    and never re-syncs, so roles would go stale. We override it to sync roles
    from the trusted header on every login.

NOTE on imports: this module is loaded VERY early (when create_app() applies the
config), before the Flask app is initialized. Importing superset.views.* at module
top triggers superset.models and fails with "App not initialized yet". So, exactly
like Superset's own register_views(), the view imports + the auth-view subclass are
done lazily INSIDE register_views() (which runs after init).

NOTE: the embedded-dashboard (guest token) viewer path does NOT go through this
class — it uses guest tokens and never establishes a Superset login session.
"""
import logging
# unquote_plus (not unquote): Java's URLEncoder.encode form-encodes space as '+'.
from urllib.parse import unquote_plus

from flask import request
from superset.security import SupersetSecurityManager

log = logging.getLogger(__name__)

# environ keys: the edge gateway sends HTTP headers X-Remote-*, which WSGI
# (gunicorn/werkzeug) exposes as HTTP_X_REMOTE_* in request.environ.
_ROLES_ENV = "HTTP_X_REMOTE_ROLES"
_EMAIL_ENV = "HTTP_X_REMOTE_EMAIL"
_FIRST_ENV = "HTTP_X_REMOTE_FIRSTNAME"
_LAST_ENV = "HTTP_X_REMOTE_LASTNAME"

# Permissions the embedded-dashboard SDK needs on the GUEST role: after getting a
# guest token it calls GET /api/v1/me/roles (CurrentUserRestApi.can_read) as a session
# check. The builtin Gamma role omits it, so a fresh DB / `superset init` produces a
# guest that gets 403 there -> the iframe shows "embedded authentication" failure.
# We (re)grant it in sync_role_definitions so it survives every init/upgrade/new DB.
_GUEST_EMBED_PVMS = [("can_read", "CurrentUserRestApi")]


class PlatformRemoteUserSecurityManager(SupersetSecurityManager):
    def register_views(self) -> None:
        # Lazy imports (see module docstring) — these pull in superset.views.*,
        # which is only safe once the app is initialized (register_views runs then).
        from urllib.parse import urlparse

        from flask import g, redirect
        from flask_appbuilder import expose
        from flask_login import login_user
        from superset.views.auth import SupersetAuthView, SupersetRegisterUserView

        class RemoteUserSupersetAuthView(SupersetAuthView):
            """Superset's /login view, but authenticates from the trusted
            REMOTE_USER header (gateway-injected) instead of only rendering."""

            @expose("/")
            def login(self, provider=None):
                if g.user is not None and g.user.is_authenticated:
                    return redirect(self.appbuilder.get_url_for_index)
                sm = self.appbuilder.sm
                username = request.environ.get(sm.auth_remote_user_env_var)
                if username:
                    user = sm.auth_user_remote_user(username)
                    if user is not None:
                        login_user(user)
                        nxt = request.args.get("next") or ""
                        parsed = urlparse(nxt)
                        # same-host or relative only (no open redirect)
                        if nxt and (not parsed.netloc or parsed.netloc == request.host):
                            return redirect(nxt)
                        return redirect(self.appbuilder.get_url_for_index)
                # No trusted header / unknown user: Superset's rendered login page.
                return super().login()

        # Mirror Superset 6.0's own register_views() EXACTLY, but bind /login to the
        # REMOTE_USER-aware view. Keeping the rest (esp. removing the legacy user/role/
        # group CRUD views + menu items) is what stops the blank "List Users" page.
        self.auth_view = self.appbuilder.add_view_no_menu(RemoteUserSupersetAuthView)
        self.registeruser_view = self.appbuilder.add_view_no_menu(SupersetRegisterUserView)

        super(SupersetSecurityManager, self).register_views()

        for view in list(self.appbuilder.baseviews):
            if isinstance(view, self.rolemodelview.__class__) and getattr(
                view, "route_base", None
            ) in ["/roles", "/users", "/groups", "registrations"]:
                self.appbuilder.baseviews.remove(view)

        security_menu = next(
            (m for m in self.appbuilder.menu.get_list() if m.name == "Security"), None
        )
        if security_menu:
            for item in list(security_menu.childs):
                if item.name in [
                    "List Roles",
                    "List Users",
                    "List Groups",
                    "User Registrations",
                ]:
                    security_menu.childs.remove(item)
        log.info("PlatformRemoteUserSecurityManager: /login bound to REMOTE_USER auth view")

    def sync_role_definitions(self) -> None:
        # Runs on `superset init` (incl. fresh DB / redeploy). The builtin sync RESETS
        # each role's permissions to its default set, so any manual grant on Gamma is
        # wiped here — that's why a one-off SQL grant doesn't survive. We re-apply it
        # every time: create the embed permission-views first (so Admin/Alpha pick them
        # up via their normal "all pvms" grant), run the builtin sync, then explicitly
        # grant them to the guest role.
        for perm, view in _GUEST_EMBED_PVMS:
            self.add_permission_view_menu(perm, view)
        super().sync_role_definitions()
        self._grant_guest_embed_permissions()

    def _grant_guest_embed_permissions(self) -> None:
        from flask import current_app

        role = self.find_role(current_app.config.get("GUEST_ROLE_NAME", "Public"))
        if role is None:
            return
        for perm, view in _GUEST_EMBED_PVMS:
            pvm = self.find_permission_view_menu(perm, view)
            if pvm is not None:
                self.add_permission_role(role, pvm)  # idempotent
        log.info("Ensured embed permissions on guest role '%s'", role.name)

    def _resolve_roles(self):
        raw = request.environ.get(_ROLES_ENV, "") or ""
        names = [n.strip() for n in raw.split(",") if n.strip()]
        roles = [r for r in (self.find_role(n) for n in names) if r is not None]
        if not roles:
            # No mapped/known roles -> minimal default (fail safe, see config).
            default = self.find_role(self.auth_user_registration_role)
            roles = [default] if default else []
        return roles

    def _profile_from_headers(self, username):
        # Gateway-injected, trusted identity. Firstname is URL-encoded (may be non-ASCII).
        email = request.environ.get(_EMAIL_ENV) or (username + "@email.notfound")
        first = unquote_plus(request.environ.get(_FIRST_ENV, "")) or username
        last = unquote_plus(request.environ.get(_LAST_ENV, "")) or "-"
        return email, first, last

    def auth_user_remote_user(self, username):
        roles = self._resolve_roles()
        email, first, last = self._profile_from_headers(username)
        user = self.find_user(username=username)

        if user is None:
            if not self.auth_user_registration:
                log.warning("REMOTE_USER %s unknown and registration disabled", username)
                return None
            user = self.add_user(
                username=username,
                first_name=first,
                last_name=last,
                email=email,
                role=roles,
            )
            if not user:
                log.error("Failed to JIT-create REMOTE_USER %s", username)
                return None
            log.info("JIT-created %s (email=%s) roles=%s", username, email, [r.name for r in user.roles])
        else:
            if not user.is_active:
                return None
            # Re-sync roles AND profile (email/name) from the trusted headers every login,
            # so existing users get corrected after a platform-side change.
            user.roles = roles
            user.email = email
            user.first_name = first
            user.last_name = last
            self.update_user(user)

        self.update_user_auth_stat(user)
        return user
