#!/usr/bin/env python3
"""Review opt-in recognition reports. Keys come from environment, never command-line logs.

LEARNING_SERVER_URL, APP_KEY, FEEDBACK_ADMIN_KEY must be set by the operator.
list shows candidates; inspect CONTEXT shows supporting reports; crop REPORT_ID saves
one private JPEG; approve CONTEXT CARD_ID or disable CONTEXT CARD_ID publishes/rolls back.
Private downloaded photos belong outside the git repository.
"""
import argparse, json, os, urllib.request, urllib.parse
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('action',choices=['list','inspect','crop','approve','disable'])
p.add_argument('args',nargs='*');p.add_argument('--output',type=Path)
a=p.parse_args()
base=os.environ['LEARNING_SERVER_URL'].rstrip('/')+'/v1/feedback/moderate'
headers={'X-App-Key':os.environ['APP_KEY'],'Authorization':'Bearer '+os.environ['FEEDBACK_ADMIN_KEY'],'Content-Type':'application/json'}
data=None
if a.action in ['approve','disable']:
 if len(a.args)!=2:p.error('Supply CONTEXT CARD_ID')
 data=json.dumps({'context':a.args[0],'cardId':a.args[1],'disable':a.action=='disable'}).encode()
if a.action in ['inspect','crop']:
 if len(a.args)!=1:p.error('Supply one context or report ID')
 base+='?'+urllib.parse.urlencode({a.action:a.args[0]})
if a.action=='crop' and not a.output:p.error('Use --output outside the repository')
with urllib.request.urlopen(urllib.request.Request(base,data=data,headers=headers),timeout=30) as response:
 body=response.read()
 if a.action=='crop': a.output.write_bytes(body);print('Private card crop downloaded.')
 else: print(json.dumps(json.loads(body),ensure_ascii=False,indent=2))
