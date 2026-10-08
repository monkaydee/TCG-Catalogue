#!/usr/bin/env python3
"""Authenticated deployment smoke checks. Credentials are never printed or persisted."""
import json
import math
import os
import subprocess
import time
from urllib.parse import urlencode
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
        if status.get("priceMatchingRevision") == 8:
            break
        if attempt == 8:
            raise AssertionError("Updated price service did not propagate")
        time.sleep(5)
    assert status["learning"]["privatePhotos"] is False, "Unmoderated learning photos must be disabled"
    assert status["learning"]["metadataRevision"] == 2
    # Fetching rules applies the one-time removal of legacy photos/free OCR evidence.
    rules=call("/v1/feedback/rules")
    assert isinstance(json.loads(rules["payload"]),list)
    status=call("/v1/status")
    assert status["learning"]["legacyMigrationComplete"] is True, "Legacy learning cleanup did not complete"
    config='header = '+json.dumps('X-App-Key: '+key)+'\n'
    rejection=subprocess.check_output(["curl","--silent","--show-error","--max-time","30","--config","-","-H","Content-Type: application/json","--data-raw",json.dumps({"image":"blocked-fixture","photoApproved":True}),"--write-out","\n%{http_code}",url+"/v1/feedback"],input=config.encode())
    rejection_body,rejection_code=rejection.rsplit(b"\n",1)
    assert rejection_code==b"422" and json.loads(rejection_body).get("error")=="photo_uploads_disabled", "Shared photo rejection failed"
    # Use native Japanese release identities; translating an English collector number is invalid.
    fixtures = [
        {"game":"POKEMON","id":"base1-4","name":"Charizard","set":"Base Set","number":"4/102","tcgplayerId":"42382","printing":"Holofoil","language":"EN","market":"US","graded":True},
        {"game":"POKEMON","id":"base1-4","name":"Charizard","localName":"Glurak","set":"Base Set","setAliases":["Basis","Grundset"],"number":"4/102","printing":"Holofoil","language":"DE","market":"DE","graded":True},
        {"game":"POKEMON","id":"ja:SV2a-025","name":"ピカチュウ","localName":"Pikachu","set":"Pokemon Card 151","setAliases":["ポケモンカード151","SV2a"],"number":"25/165","language":"JA","market":"DE","graded":True},
        {"game":"POKEMON","id":"sv10-193","name":"Misty's Psyduck","set":"Destined Rivals","number":"193/182","tcgplayerId":"632993","printing":"Holofoil","printingUnique":True,"language":"EN","market":"US","graded":True},
        {"game":"ONE_PIECE","id":"OP01-001","name":"Roronoa Zoro","set":"Romance Dawn","number":"OP01-001","language":"EN","market":"US","graded":True},
        {"game":"ONE_PIECE","id":"OP01-001","name":"Roronoa Zoro","set":"Romance Dawn","number":"OP01-001","language":"JA","market":"DE","graded":True},
        {"game":"POKEMON","id":"base5-4","name":"Dark Charizard","set":"Team Rocket","number":"4/82","tcgplayerId":"84572","printing":"1st Edition Holofoil","printingUnique":True,"language":"EN","market":"DE","graded":True,"grader":"PSA","grade":"5","gradedOnly":True},
        {"game":"POKEMON","id":"base2-3","name":"Flareon","set":"Jungle","number":"3/64","tcgplayerId":"45129","printing":"Holofoil","printingUnique":True,"language":"EN","market":"DE","graded":True,"grader":"PSA","grade":"8","gradedOnly":True},
        {"game":"POKEMON","id":"sv10-193","name":"Misty's Psyduck","localName":"Mistys Enton","set":"Destined Rivals","setAliases":["Ewige Rivalen"],"number":"193/182","printing":"Holofoil","printingUnique":True,"language":"DE","market":"DE","graded":True,"grader":"CGC","grade":"9","gradedOnly":True},
    ]
    rows=[]
    for card in fixtures:
        for attempt in range(9):
            result=call("/v1/prices", {"schemaVersion":2,"cards":[card]})
            if all(r.get("key", "").startswith("v8:") for r in result.get("results",[])):
                break
            if attempt == 8:
                raise AssertionError("Price request still served an older matching revision")
            time.sleep(5)
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
                assert grade.get("listings",0)>=1 and grade["low"]<=grade["price"]<=grade["high"]
                assert "asking" in grade["source"]
                if grade["listings"]<5:
                    assert grade.get("evidence")=="limited"
        if "eBay" in (row.get("source") or ""):
            assert row.get("market") is None, "Condition-mixed asking price cannot become NM"
    assert len({row["key"] for row in rows})==len(rows), "Language/market cache collision"
    sealed=[]
    for name,language,product_id,aliases,market,game in [
        ("Terastal Festival ex Booster Box","JA","-1",["SV8a","テラスタルフェスex"],"US","POKEMON"),
        ("Terastal Festival ex Booster Box","JA","-1",["SV8a","テラスタルフェスex"],"DE","POKEMON"),
        ("Surging Sparks Booster Box","DE","-784949",["Stürmische Funken"],"DE","POKEMON"),
        ("Black Bolt Booster Bundle","DE","-824107",["Black Bolt","Schwarze Blitze"],"DE","POKEMON"),
        ("White Flare Elite Trainer Box","DE","-824089",["Weiße Flammen","White Flare"],"DE","POKEMON"),
        ("Romance Dawn Booster Box","JA","-2",["OP01","ロマンスドーン"],"DE","ONE_PIECE"),
        ("Two Legends Booster Box (Non-English)","JA","-766868",["OP08","Two Legends","二つの伝説"],"DE","ONE_PIECE"),
        ("The Azure Sea's Seven Japanese Booster Box","JA","-9000000010142",["OP14","蒼海の七傑","The Azure Sea's Seven"],"DE","ONE_PIECE"),
        ("The Azure Sea's Seven Japanese Booster Pack","JA","-9000000010141",["OP14","蒼海の七傑","The Azure Sea's Seven"],"DE","ONE_PIECE"),
    ]:
        for attempt in range(9):
            answer=call("/v1/sealed/price",{"game":game,"productId":product_id,"name":name,"language":language,"aliases":aliases,"market":market})
            if answer.get("sealedMatchingRevision")==9:
                break
            if attempt==8:
                raise AssertionError("Updated sealed matching did not propagate")
            time.sleep(5)
        quote=answer.get("price")
        if quote:
            assert quote["listings"]>=1 and quote["currency"] in ["EUR","USD"]
            if quote["currency"]!=("EUR" if market=="DE" else "USD"):
                assert "international reference" in quote["source"]
            assert quote["low"]<=quote["amount"]<=quote["high"]
        if quote and quote["listings"] < 5:
            assert quote.get("evidence") == "limited"
        if answer.get("imageUrl"):
            assert answer["imageUrl"].startswith("https://i.ebayimg.com/")
        sealed.append({"name":name,"language":language,"market":market,**answer})
    # Inspect graded-provider access directly: HTTP/schema/counts only, never credentials.
    provider_probe={"configured":bool(os.environ.get("JUSTTCG_KEY"))}
    if provider_probe["configured"]:
        config='header = '+json.dumps('x-api-key: '+os.environ["JUSTTCG_KEY"])+ '\n'
        query=urlencode({"tcgplayer_id":"45129","graded":"only","grading_company":"PSA","grade":"8"})
        try:
            response=subprocess.check_output(["curl","--silent","--show-error","--max-time","30","--config","-","--write-out","\n%{http_code}","https://api.justtcg.com/v2/cards?"+query],input=config.encode())
            body,code=response.rsplit(b"\n",1)
            provider_probe["httpStatus"]=int(code)
            if code==b"200":
                data=json.loads(body).get("data",[])
                variants=[v for c in data for v in c.get("variants",[]) if v.get("type")=="graded"]
                provider_probe["gradedVariants"]=len(variants)
                provider_probe["pricedGradedVariants"]=sum(any(isinstance(m.get("price"),(int,float)) and m["price"]>0 for m in v.get("markets",[])) for v in variants)
        except (subprocess.SubprocessError,ValueError,TypeError,AttributeError):
            provider_probe["probeFailed"]=True
    report={"status":status,"cards":rows,"sealed":sealed,"gradedProviderProbe":provider_probe}
    Path("price-verification.json").write_text(json.dumps(report,indent=2))
    for item in sealed:
        if item["name"] in ["Surging Sparks Booster Box", "Two Legends Booster Box (Non-English)", "The Azure Sea's Seven Japanese Booster Box", "The Azure Sea's Seven Japanese Booster Pack"]:
            assert item.get("price"), "No exact-language sealed quote for regression fixture: "+json.dumps(item)
    assert any(row.get("graded") for row in rows), "No graded quotes returned across the entire live fixture set; see price-verification.json"
    print("Live API: photo rejection/legacy cleanup, schema, source, currency and language separation checks passed.")
    print("Graded provider access:",json.dumps(provider_probe))
    for card,row in zip(fixtures,rows):
        print(card["game"],card["language"],row.get("source") or row.get("reason"),row.get("conditions"), "graded comparables:",len(row.get("graded",[])))
    for item in sealed:
        print(item["language"],item["market"],item["name"],item.get("price") or item.get("reason"))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"],"a") as f:
            f.write("\n### Live price verification\n\n```json\n"+json.dumps(report,indent=2)+"\n```\n")

if __name__ == "__main__":
    main()
