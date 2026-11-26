"""Read-only cloud checks. Credentials come from an external private file."""
import argparse
import datetime
import json
from pathlib import Path
import time
import urllib.request
import urllib.error


def request(url, method="GET", body=None, token=None, headers=None):
    hdr = {"Accept": "application/json", **(headers or {})}
    if token:
        hdr["Authorization"] = "Bearer " + token
    payload = None if body is None else json.dumps(body).encode()
    if payload is not None:
        hdr["Content-Type"] = "application/json"
    start = time.monotonic()
    req = urllib.request.Request(url, data=payload, headers=hdr, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    except (OSError, TimeoutError):
        return 0, {}, round(time.monotonic() - start, 3)
    try:
        data = json.loads(raw)
    except (ValueError, UnicodeError):
        data = {}
    return status, data, round(time.monotonic() - start, 3)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="https://backend-restopilot.onrender.com")
    parser.add_argument("--accounts", required=True)
    parser.add_argument("--output", default="artifacts/cloud-smoke/results.json")
    args = parser.parse_args()
    if args.base != "https://backend-restopilot.onrender.com":
        parser.error("Only the authorized Render service is supported")
    config = json.loads(Path(args.accounts).read_text(encoding="utf-8-sig"))
    if config["supabaseUrl"] != "https://unvxosrwqmcwrtxplrsf.supabase.co":
        parser.error("Only the configured Supabase project is supported")
    results = []
    base = args.base

    def check(name, response, expected, validate=lambda body: True):
        status, body, seconds = response
        passed = status in expected and validate(body)
        result = dict(test=name, status=status, seconds=seconds, passed=passed)
        results.append(result)
        print(json.dumps(result), flush=True)
        return body if passed else None

    check("database_readiness", request(base + "/health/ready"), [200], lambda b: b.get("status") == "UP")
    for route in ["cliente", "admin"]:
        check("anonymous_" + route, request(base + "/api/ia/" + route + "/chat", "POST", {"mensaje": "Hola"}), [401])
    check("invalid_token", request(base + "/api/ia/admin/chat", "POST", {"mensaje": "Hola"}, "invalid-test-token"), [401])
    for role, credentials in config["accounts"].items():
        auth = check("supabase_login_" + role, request(config["supabaseUrl"] + "/auth/v1/token?grant_type=password", "POST", credentials,
                     headers={"apikey": config["publishableKey"]}), [200], lambda b: bool(b.get("access_token")))
        if not auth:
            continue
        token = auth["access_token"]
        expected_role = "CLIENTE" if role == "client" else "DUENO" if role == "owner" else "ADMINISTRADOR"
        profile = check("database_identity_" + role, request(base + "/api/auth/me", token=token), [200], lambda b: b.get("rol") == expected_role)
        wrong = "admin" if role == "client" else "cliente"
        check("role_isolation_" + role, request(base + "/api/ia/" + wrong + "/chat", "POST", {"mensaje": "Hola"}, token), [403])
        if role == "client":
            check("client_own_order", request(base + "/api/ia/cliente/chat", "POST", {"mensaje": "Cual es mi ultimo pedido?"}, token),
                  [200], lambda b: b.get("estado") in ["ANSWER", "NO_DATA"])
        elif profile:
            local = profile.get("restauranteId")
            check("active_restaurant_" + role, request(base + "/api/restaurantes/" + str(local), token=token), [200], lambda b: b.get("id") == local)
            check("gemini_report_" + role, request(base + "/api/ia/admin/chat", "POST",
                  {"mensaje": "Cuales fueron los pagos del 1 al 30 de noviembre de 2026?", "restauranteId": local}, token),
                  [200], lambda b: b.get("estado") == "NO_DATA" or (
                      b.get("estado") == "ANSWER" and isinstance(b.get("datos"), dict)
                      and b["datos"].get("fechaInicio") == "2026-11-01"
                      and b["datos"].get("fechaFin") == "2026-11-30"
                      and b["datos"].get("tipo") == "PAGOS"))
            listed = request(base + "/api/restaurantes", token=token)
            other = next((r.get("id") for r in listed[1] if r.get("id") != local), None) if listed[0] == 200 and isinstance(listed[1], list) else None
            if other:
                check("restaurant_isolation_" + role, request(base + "/api/ia/admin/chat", "POST",
                      {"mensaje": "Pagos de noviembre 2026", "restauranteId": other}, token), [403])
    report = dict(timestamp=datetime.datetime.now(datetime.timezone.utc).isoformat(), base=base,
                  writes="No business writes; login and read-only queries only", results=results,
                  passed=all(r["passed"] for r in results))
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
