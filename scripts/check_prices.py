#!/usr/bin/env python3
"""Authenticated deployment smoke checks. Credentials are never printed or persisted."""
import json
import math
import os
import subprocess
from pathlib import Path

def main():
    url = os.environ["URL"].rstrip("/")
    key = os.environ["APP_KEY"]
    def call(path, data=None):
        # Match the deployment's curl health check; Cloudflare rejects urllib's default agent.
        # Keep authentication out of process arguments and error output.
        config = 'header = ' + json.dumps('X-App-Key: ' + key) + '\n'
        args = ["curl", "--fail", "--silent", "--show-error", "--max-time", "90", "--config", "-", url + path]
        if data is not None:
            args += ["-H", "Content-Type: application/json", "--data-raw", json.dumps(data)]
        return json.loads(subprocess.check_output(args, input=config.encode()))
    status = call("/v1/status")
    # Non-English card must get its own key and may abstain, never inherit the English result.
    cards = [{"game":"POKEMON","id":"base1-58","name":"Pikachu","set":"Base Set","number":"58/102","language":lang} for lang in ["EN","DE","JA"]]
    result = call("/v1/prices", {"schemaVersion":2,"cards":cards})
    rows = result.get("results", [])
    assert len(rows) == len(cards), "Missing price results"
    assert len({r["key"] for r in rows}) == len(cards), "Language cache collision"
    for row in rows:
        for amount in [row.get("market")] + list((row.get("conditions") or {}).values()):
            assert amount is None or isinstance(amount,(int,float)) and math.isfinite(amount) and amount > 0
        if row.get("market") is not None or any(v is not None for v in (row.get("conditions") or {}).values()):
            assert row.get("source") and row.get("currency") in ["USD","EUR"]
    sealed = []
    for name, language in [("Terastal Festival ex Booster Box","JA"),("Surging Sparks Booster Box","DE")]:
        answer=call("/v1/sealed/price",{"game":"POKEMON","productId":"-1","name":name,"language":language})
        p=answer.get("price")
        if p:
            assert p["listings"]>=3 and p["currency"] == ("EUR" if language=="DE" else "USD")
            assert p["low"] <= p["amount"] <= p["high"]
        sealed.append({"name":name,"language":language,**answer})
    report={"status":status,"cards":rows,"sealed":sealed}
    Path("price-verification.json").write_text(json.dumps(report,indent=2))
    print("Live price API: schema, source, currency and language separation checks passed.")
    for card,row in zip(cards,rows):
        print(card["language"], row.get("source") or row.get("reason"), row.get("currency"), row.get("market"))
    for item in sealed:
        print(item["language"],item["name"],item.get("price") or item.get("reason"))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"],"a") as f:
            f.write("\n### Live price verification\n\n```json\n"+json.dumps(report,indent=2)+"\n```\n")

if __name__ == "__main__":
    main()
