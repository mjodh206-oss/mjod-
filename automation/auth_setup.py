#!/usr/bin/env python3
"""One-time OAuth setup — run locally, in a browser, once.

Produces an authorized-user token JSON containing a refresh_token. Store it as:
  * local:      token.json (gitignored) referenced by GOOGLE_TOKEN_JSON in .env
  * automation: GitHub Actions Secret named YT_TOKEN_JSON (paste the JSON, no newlines needed)

IMPORTANT: never paste this token into a chat, a commit, or a public file.

Usage:
    python automation/auth_setup.py --client-secret /path/to/client_secret.json
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

from common import ROOT, SCOPES, load_dotenv, read_secret


def main() -> int:
    load_dotenv()
    ap = argparse.ArgumentParser(description="One-time YouTube OAuth token setup")
    ap.add_argument("--client-secret", default=os.environ.get("GOOGLE_CLIENT_SECRET_JSON", "client_secret.json"),
                    help="path to the OAuth client JSON, or set GOOGLE_CLIENT_SECRET_JSON")
    ap.add_argument("--write", default="token.json", help="local output file (gitignored)")
    ap.add_argument("--print", action="store_true", default=True, help="print the token JSON (default)")
    args = ap.parse_args()

    client = read_secret("GOOGLE_CLIENT_SECRET_JSON", "YT_CLIENT_SECRET_JSON")
    if client is None:
        path = Path(args.client_secret).expanduser()
        if not path.exists():
            print(f"ERROR: client secret not found at {path}\n"
                  "Create an OAuth 2.0 'Desktop app' client in Google Cloud Console,\n"
                  "enable the YouTube Data API v3 + YouTube Analytics API, and download the JSON.",
                  file=sys.stderr)
            return 2
        client = json.loads(path.read_text(encoding="utf-8"))

    try:
        from google_auth_oauthlib.flow import InstalledAppFlow
    except ImportError:
        print("ERROR: run `python3 -m pip install -r requirements.txt` first", file=sys.stderr)
        return 2

    print("Opening a browser for Google consent (offline access / refresh token)…")
    flow = InstalledAppFlow.from_client_config(client, SCOPES)
    creds = flow.run_local_server(port=0, open_browser=True, prompt="consent", access_type="offline")

    token_json = creds.to_json()
    out = Path(args.write).expanduser()
    if not out.is_absolute():
        out = ROOT / out
    out.write_text(token_json + "\n", encoding="utf-8")

    print("\n✓ Token written to " + str(out.relative_to(ROOT) if out.is_relative_to(ROOT) else out))
    print("  Add to .env:  GOOGLE_TOKEN_JSON=" + str(out))
    print("\nFor GitHub Actions, add TWO repository Secrets:")
    print("  YT_CLIENT_SECRET_JSON = the client JSON contents")
    print("  YT_TOKEN_JSON         = this token JSON contents")
    if args.print:
        print("\n--- TOKEN JSON (store as a secret, never share) ---")
        print(token_json)
        print("--- END ---")
    print("\nScopes granted: " + ", ".join(SCOPES))
    return 0


if __name__ == "__main__":
    sys.exit(main())
