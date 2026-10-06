#!/usr/bin/env python3
"""Authenticated deployment smoke checks. Credentials are never printed or persisted."""
import json
import math
import os
import subprocess
import time
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
    # workers.dev edges can briefly serve the previous version after deployment.
    for attempt in range(9):
        status = call("/v1/status")
        if status.get("priceMatchingRevision") == 5:
            break
        if attempt == 8:
            raise AssertionError("Updated price service did not propagate")
        time.sleep(5)
    # Use native Japanese release identities; translating an English collector number is invalid.
    fixtures = [
        {"game":"POKEMON","id":"base1-4","name":"Charizard","set":"Base Set","number":"4/102","tcgplayerId":"42382","printing":"Holofoil","language":"EN","market":"US","graded":True},
        {"game":"POKEMON","id":"base1-4","name":"Charizard","localName":"Glurak","set":"Base Set","setAliases":["Basis","Grundset"],"number":"4/102","printing":"Holofoil","language":"DE","market":"DE","graded":True},
        {"game":"POKEMON","id":"ja:SV2a-025","name":"ピカチュウ","localName":"Pikachu","set":"Pokemon Card 151","setAliases":["ポケモンカード151","SV2a"],"number":"25/165","language":"JA","market":"DE","graded":True},
        {"game":"POKEMON","id":"sv10-193","name":"Misty's Psyduck","set":"Destined Rivals","number":"193/182","tcgplayerId":"632993","printing":"Holofoil","printingUnique":True,"language":"EN","market":"US","graded":True},
        {"game":"ONE_PIECE","id":"OP01-001","name":"Roronoa Zoro","set":"Romance Dawn","number":"OP01-001","language":"EN","market":"US","graded":True},
        {"game":"ONE_PIECE","id":"OP01-001","name":"Roronoa Zoro","set":"Romance Dawn","number":"OP01-001","language":"JA","market":"DE","graded":True},
    ]
    rows=[]
    for card in fixtures:
        result=call("/v1/prices", {"schemaVersion":2,"cards":[card]})
        assert len(result.get("results",[]))==1, "Missing price result"
        row=result["results"][0]; rows.append(row)
        for amount in [row.get("market")] + list((row.get("conditions") or {}).values()):
            assert amount is None or isinstance(amount,(int,float)) and math.isfinite(amount) and amount>0
        if row.get("market") is not None or row.get("conditions"):
            assert row.get("source") and row.get("currency") in ["USD","EUR"]
            if row.get("fetchedAt") is not None:
                assert isinstance(row["fetchedAt"],str)
        for grade in row.get("graded",[]):
            assert grade["price"]>0 and math.isfinite(grade["price"])
            assert grade["currency"] in ["USD","EUR"] and grade.get("source")
            assert grade.get("fetchedAt") and isinstance(grade.get("stale"),bool), "Missing graded freshness: " + json.dumps(row)
            if "eBay" in grade["source"]:
                assert grade.get("listings",0)>=5 and grade["low"]<=grade["price"]<=grade["high"]
        if "eBay" in (row.get("source") or ""):
            assert row.get("market") is None, "Condition-mixed asking price cannot become NM"
    assert len({row["key"] for row in rows})==len(rows), "Language/market cache collision"
    sealed=[]
    for name,language,product_id,aliases,market,game in [
        ("Terastal Festival ex Booster Box","JA","-1",["SV8a","テラスタルフェスex"],"US","POKEMON"),
        ("Terastal Festival ex Booster Box","JA","-1",["SV8a","テラスタルフェスex"],"DE","POKEMON"),
        ("Surging Sparks Booster Box","DE","-784949",["Stürmische Funken"],"DE","POKEMON"),
        ("Romance Dawn Booster Box","JA","-2",["OP01","ロマンスドーン"],"DE","ONE_PIECE"),
    ]:
        answer=call("/v1/sealed/price",{"game":game,"productId":product_id,"name":name,"language":language,"aliases":aliases,"market":market})
        quote=answer.get("price")
        if quote:
            assert quote["listings"]>=3 and quote["currency"]==("EUR" if market=="DE" else "USD")
            assert quote["low"]<=quote["amount"]<=quote["high"]
        sealed.append({"name":name,"language":language,"market":market,**answer})
    report={"status":status,"cards":rows,"sealed":sealed}
    Path("price-verification.json").write_text(json.dumps(report,indent=2))
    print("Live price API: schema, source, currency and language separation checks passed.")
    for card,row in zip(fixtures,rows):
        print(card["game"],card["language"],row.get("source") or row.get("reason"),row.get("conditions"), "graded comparables:",len(row.get("graded",[])))
    for item in sealed:
        print(item["language"],item["market"],item["name"],item.get("price") or item.get("reason"))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"],"a") as f:
            f.write("\n### Live price verification\n\n```json\n"+json.dumps(report,indent=2)+"\n```\n")

if __name__ == "__main__":
    main()
