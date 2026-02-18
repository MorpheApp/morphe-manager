#!/usr/bin/env python3
"""
Send an FCM push notification to a Morphe Manager topic.

Environment variables (all required):
  FCM_PROJECT_ID           — Firebase project ID (from Firebase Console)
  FCM_SERVICE_ACCOUNT_JSON — Full content of the Service Account JSON file

Optional (for release workflow):
  NEW_TAG   — Release tag, e.g. "v1.2.3". Version is derived by stripping the "v" prefix.
  BRANCH    — Git branch name. "main" → stable topic; anything else → dev topic.

Optional (for test workflow — override auto-routing):
  FCM_TOPIC   — Explicit topic to send to. Overrides BRANCH-based routing.
  FCM_TYPE    — Message type: "manager_update" (default) or "bundle_update"
  FCM_VERSION — Version string to include in manager_update messages (default: "test")
"""

import json
import os
import sys
import time
import base64
import urllib.request
import urllib.parse

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding as asym_padding
except ImportError:
    print("ERROR: 'cryptography' package is not installed. Run: pip install cryptography", file=sys.stderr)
    sys.exit(1)

# ── Read environment ───────────────────────────────────────────────────────────

project_id = os.environ.get("FCM_PROJECT_ID", "").strip()
sa_json    = os.environ.get("FCM_SERVICE_ACCOUNT_JSON", "").strip()

if not project_id:
    print("ERROR: FCM_PROJECT_ID is not set", file=sys.stderr)
    sys.exit(1)
if not sa_json:
    print("ERROR: FCM_SERVICE_ACCOUNT_JSON is not set", file=sys.stderr)
    sys.exit(1)

# Topic routing:
#   FCM_TOPIC env var overrides everything (used in test workflow for manual control)
#   Otherwise: main → stable topic, anything else → dev topic
explicit_topic = os.environ.get("FCM_TOPIC", "").strip()
branch         = os.environ.get("BRANCH", "").strip()
new_tag        = os.environ.get("NEW_TAG", "").strip()

# Message type and version
msg_type    = os.environ.get("FCM_TYPE", "manager_update").strip()
fcm_version = os.environ.get("FCM_VERSION", "").strip()

# For release workflow: derive version from tag (strip leading "v")
if new_tag:
    fcm_version = new_tag.lstrip("v")

if explicit_topic:
    topic = explicit_topic
else:
    # Default routing by message type and branch:
    #   manager_update: morphe_updates / morphe_updates_dev
    #   bundle_update:  morphe_patches_updates / morphe_patches_updates_dev
    is_main = (branch == "main")
    if msg_type == "bundle_update":
        topic = "morphe_patches_updates" if is_main else "morphe_patches_updates_dev"
    else:
        topic = "morphe_updates" if is_main else "morphe_updates_dev"

print(f"FCM target   : {topic}")
print(f"Message type : {msg_type}")
if fcm_version:
    print(f"Version      : {fcm_version}")

# ── Parse Service Account ──────────────────────────────────────────────────────

try:
    sa = json.loads(sa_json)
except json.JSONDecodeError as e:
    print(f"ERROR: FCM_SERVICE_ACCOUNT_JSON is not valid JSON: {e}", file=sys.stderr)
    sys.exit(1)

required_keys = ("client_email", "private_key", "token_uri")
for key in required_keys:
    if key not in sa:
        print(f"ERROR: FCM_SERVICE_ACCOUNT_JSON missing key: '{key}'", file=sys.stderr)
        sys.exit(1)

# ── Build and sign JWT ─────────────────────────────────────────────────────────

now = int(time.time())

def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()

header  = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
payload = b64(json.dumps({
    "iss":   sa["client_email"],
    "sub":   sa["client_email"],
    "aud":   sa.get("token_uri", "https://oauth2.googleapis.com/token"),
    "iat":   now,
    "exp":   now + 3600,
    "scope": "https://www.googleapis.com/auth/firebase.messaging",
}).encode())

try:
    private_key = serialization.load_pem_private_key(
        sa["private_key"].encode(),
        password=None,
    )
except Exception as e:
    print(f"ERROR: Failed to load private key from Service Account: {e}", file=sys.stderr)
    sys.exit(1)

signature = private_key.sign(
    f"{header}.{payload}".encode(),
    asym_padding.PKCS1v15(),
    hashes.SHA256(),
)
jwt_token = f"{header}.{payload}.{b64(signature)}"

# ── Exchange JWT for OAuth2 access token ──────────────────────────────────────

token_url  = sa.get("token_uri", "https://oauth2.googleapis.com/token")
token_data = urllib.parse.urlencode({
    "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
    "assertion":  jwt_token,
}).encode()

try:
    token_resp = urllib.request.urlopen(
        urllib.request.Request(token_url, data=token_data)
    )
    access_token = json.loads(token_resp.read())["access_token"]
except Exception as e:
    print(f"ERROR: Failed to obtain access token: {e}", file=sys.stderr)
    sys.exit(1)

print("OAuth2 token obtained successfully")

# ── Build FCM message payload ──────────────────────────────────────────────────

data_payload: dict[str, str] = {"type": msg_type}
# Include version in payload whenever it's provided - both manager_update and
# bundle_update support it; the app ignores unknown keys for forward-compatibility.
if fcm_version:
    data_payload["version"] = fcm_version

fcm_message = {
    "message": {
        "topic": topic,
        "data":  data_payload,
        # high priority wakes the device from Doze mode via Google Play Services
        "android": {"priority": "high"},
    }
}

# ── Send FCM request ───────────────────────────────────────────────────────────

fcm_url     = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
fcm_payload = json.dumps(fcm_message).encode()

fcm_req = urllib.request.Request(
    fcm_url,
    data=fcm_payload,
    headers={
        "Authorization": f"Bearer {access_token}",
        "Content-Type":  "application/json",
    },
)

try:
    fcm_resp    = urllib.request.urlopen(fcm_req)
    status_code = fcm_resp.status
    resp_body   = fcm_resp.read().decode()
except urllib.error.HTTPError as e:
    status_code = e.code
    resp_body   = e.read().decode()

print(f"\nFCM response (HTTP {status_code}):")
try:
    print(json.dumps(json.loads(resp_body), indent=2))
except Exception:
    print(resp_body)

if status_code != 200:
    print(
        f"\nERROR: FCM push failed with HTTP {status_code}.\n"
        "Check that FCM_PROJECT_ID and FCM_SERVICE_ACCOUNT_JSON secrets are correct\n"
        "and that the Service Account has the 'Firebase Cloud Messaging Admin' role.",
        file=sys.stderr,
    )
    sys.exit(1)

print(f"\nFCM notification sent successfully to topic '{topic}'")
