"""Integration tests against real Docker services, including authenticated negative cases."""
import concurrent.futures
import json
import time
import urllib.request
import urllib.error
import urllib.parse
import uuid
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENV = dict(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines() if '=' in line)
BASE = 'http://localhost:8181'
checks = []
def request(path, token=None, method='GET', body=None, key=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    if key: headers['Idempotency-Key'] = key
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            raw=response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as response:
        raw=response.read()
        try: data=json.loads(raw)
        except ValueError: data={}
        return response.code,data
def token(name):
    data=urllib.parse.urlencode({'grant_type':'client_credentials','client_id':'qa-'+name,'client_secret':ENV['QA_'+name.upper()+'_SECRET']}).encode()
    with urllib.request.urlopen('http://localhost:8180/realms/portfolio/protocol/openid-connect/token',data=data,timeout=15) as r:
        return json.load(r)['access_token']
def check(name, condition):
    if not condition: raise AssertionError(name)
    checks.append(name)
    print('PASS', name, flush=True)
def post(account, amount, description='Integration test', key=None, identity=None):
    return request('/api/accounts/'+account+'/movements',identity or alice,'POST',{'amount':amount,'description':description},key or str(uuid.uuid4()))
def eventually(predicate, timeout=40):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        if predicate(): return True
        time.sleep(1)
    return False

for attempt in range(90):
    try:
        alice=token('alice');bob=token('bob')
        if request('/api/accounts',alice)[0]==200: break
    except (OSError,ValueError): pass
    time.sleep(2)
else: raise RuntimeError('Services did not become ready within 180 seconds')

check('anonymous access rejected',request('/api/accounts')[0]==401)
check('forged bearer rejected',request('/api/accounts','forged.jwt.signature')[0]==401)
status,account=request('/api/accounts',alice,'POST',{'name':'Integration '+str(uuid.uuid4())[:8],'currency':'USD'})
check('authenticated account creation',status==201 and account['balance']=='0.00')
account_id=account['id']
check('owner can read',request('/api/accounts/'+account_id,alice)[0]==200)
check('other identity cannot read account',request('/api/accounts/'+account_id,bob)[0]==404)
check('other identity cannot write account',post(account_id,'10',identity=bob)[0]==404)
check('invalid currency rejected',request('/api/accounts',alice,'POST',{'name':'Bad','currency':'XXX'})[0]==400)
check('blank name rejected',request('/api/accounts',alice,'POST',{'name':' ','currency':'USD'})[0]==400)
check('negative page rejected',request('/api/accounts?page=-1',alice)[0]==400)
key=str(uuid.uuid4())
status,movement=post(account_id,'100.10',key=key)
check('deposit updates exact decimal balance',status==201 and movement['balance']=='100.10')
status,replay=post(account_id,'100.10',key=key)
if status!=201 or replay!=movement:
    print('Idempotency diagnostic:',status,{k:(movement.get(k),replay.get(k)) for k in set(movement)|set(replay) if movement.get(k)!=replay.get(k)},flush=True)
check('idempotent retry preserves movement',status==201 and replay==movement)
check('idempotency payload mismatch rejected',post(account_id,'101.10',key=key)[0]==409)
check('precision rejected before write',post(account_id,'0.001')[0]==400)
check('zero movement rejected',post(account_id,'0')[0]==422)
check('overdraft rejected',post(account_id,'-1000')[0]==422)
check('missing idempotency header rejected',request('/api/accounts/'+account_id+'/movements',alice,'POST',{'amount':'1','description':'Test'})[0]==400)
check('invalid account ID rejected',request('/api/accounts/not-a-uuid',alice)[0]==400)
check('rejected movements leave balance intact',request('/api/accounts/'+account_id,alice)[1]['balance']=='100.10')
time.sleep(2)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    results=list(pool.map(lambda _:post(account_id,'-80'),range(2)))
check('concurrent withdrawals cannot both spend the same balance',sorted(s for s,_ in results)[0]==201 and sum(s==201 for s,_ in results)==1 and all(s in (201,409,422) for s,_ in results))
check('balance after concurrent withdrawals',request('/api/accounts/'+account_id,alice)[1]['balance']=='20.10')
check('two immutable movements only',len(request('/api/accounts/'+account_id+'/movements',alice)[1])==2)
check('audit eventually receives events',eventually(lambda:sum(e['account_id']==account_id for e in request('/api/audit',alice)[1])==2))
check('audit events respect identity ownership',all(e['account_id']!=account_id for e in request('/api/audit',bob)[1]))
status,large=request('/api/accounts',alice,'POST',{'name':'Precision boundary','currency':'USD'})
check('precision boundary account created',status==201)
status,exact=post(large['id'],'99999999999999.99')
check('maximum decimal survives JSON without floating point loss',status==201 and exact['balance']=='99999999999999.99')
check('overflow rejected without changing balance',post(large['id'],'0.01')[0]==422 and request('/api/accounts/'+large['id'],alice)[1]['balance']=='99999999999999.99')
status,headers=None,None
with urllib.request.urlopen(BASE) as response:
    check('CSP provided at edge',bool(response.headers.get('Content-Security-Policy')))
    check('nosniff provided at edge',response.headers.get('X-Content-Type-Options')=='nosniff')
if '--resilience' in sys.argv:
    def compose(*args):
        return subprocess.run(['docker','compose',*args],cwd=ROOT,check=True,capture_output=True,text=True).stdout
    compose('stop','rabbit')
    recovery_key=str(uuid.uuid4())
    try:
        status,pending=post(account_id,'5.00','Broker outage test',key=recovery_key)
        check('ledger commits while broker is stopped',status==201)
    finally:
        compose('start','rabbit')
    check('pending event delivered after broker recovery',eventually(lambda:any(e['event_id']==recovery_key for e in request('/api/audit',alice)[1]),timeout=90))
    compose('exec','-T','ledger-db','psql','-U','app','-d','ledger','-c',"update outbox set published_at=null where id='"+recovery_key+"';")
    check('duplicate event was actually republished',eventually(lambda:compose('exec','-T','ledger-db','psql','-U','app','-d','ledger','-tAc',"select published_at is not null from outbox where id='"+recovery_key+"';").strip()=='t'))
    time.sleep(2)
    check('duplicate event has no additional effect',sum(e['event_id']==recovery_key for e in request('/api/audit',alice)[1])==1)
artifact={'testedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'checks':checks,'count':len(checks),'status':'passed'}
(ROOT/'artifacts').mkdir(exist_ok=True)
(ROOT/'artifacts'/'integration.json').write_text(json.dumps(artifact,indent=2))
print('All',len(checks),'integration checks passed.')
