#!/usr/bin/env bash
# Stáhne objednávky demo účtu (jídelna 0000, demo/demo) a uloží je jako testovací data.
# Jiný účet: tools/fetch_demo.sh <cislo_jidelny> <jmeno> <heslo>
set -euo pipefail

CISLO="${1:-0000}"
JMENO="${2:-demo}"
HESLO="${3:-demo}"

cd "$(dirname "$0")/.."
OUT="app/src/test/resources/objednavky_demo.json"
API="https://app.strava.cz/api"
HEADERS=(-H 'Content-Type: text/plain;charset=UTF-8' -H 'Referer: https://app.strava.cz/' -H 'Cookie: NEXT_LOCALE=cs')

login=$(CISLO="$CISLO" JMENO="$JMENO" HESLO="$HESLO" python3 -c '
import json, os
print(json.dumps({"cislo": os.environ["CISLO"], "jmeno": os.environ["JMENO"], "heslo": os.environ["HESLO"],
                  "zustatPrihlasen": True, "environment": "W", "lang": "CZ"}))' \
  | curl -sS -m 30 -X POST "${HEADERS[@]}" --data-binary @- "$API/login")

orders=$(LOGIN="$login" CISLO="$CISLO" python3 -c '
import json, os, sys
data = json.loads(os.environ["LOGIN"])
if data.get("state") == "error" or not data.get("sid"):
    sys.exit("Přihlášení se nepovedlo: " + str(data.get("message", data)))
print(json.dumps({"cislo": os.environ["CISLO"], "sid": data["sid"], "s5url": data.get("s5url") or "",
                  "lang": "CZ", "konto": 0, "podminka": "", "ignoreCert": "false"}))' \
  | curl -sS -m 30 -X POST "${HEADERS[@]}" --data-binary @- "$API/objednavky")

mkdir -p "$(dirname "$OUT")"
printf '%s\n' "$orders" | python3 -m json.tool --no-ensure-ascii > "$OUT"
echo "Uloženo: $OUT ($(wc -c < "$OUT") bajtů)"
