"""Audit-only real HTTP/file-H2 repeat/concurrency/reopen smoke probes."""
import concurrent.futures, datetime, hashlib, json, pathlib, subprocess, threading, time, urllib.request, urllib.error, sys
run = pathlib.Path('target/audit-evidence/2026-10-10-8a48159/'+(sys.argv[1] if len(sys.argv)>1 else '019-step3-api'))
run.mkdir(exist_ok=False)
jar = pathlib.Path('target/audit-evidence/2026-10-10-8a48159/014-step3-tests/baseline-exec.jar')
command = ['C:/Program Files/Java/jdk-26.0.2/bin/java.exe', '-jar', str(jar), '--server.port=18083',
           '--spring.datasource.url=jdbc:h2:file:./'+run.as_posix()+'/db/certiflow', '--certiflow.jobs.enabled=false']
transcript=[]; results={}; meta={'start':datetime.datetime.now().astimezone().isoformat(), 'command':command,
                               'jarSHA256':hashlib.sha256(jar.read_bytes()).hexdigest()}
def request(method,path,body=None,actor=None):
    headers={'Content-Type':'application/json'}
    if actor: headers['X-Actor-Id']=actor
    req=urllib.request.Request('http://127.0.0.1:18083/api'+path, data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=15) as r: status,data=r.status,json.load(r)
    except urllib.error.HTTPError as e: status,data=e.code,json.load(e)
    transcript.append({'method':method,'path':path,'body':body,'actor':actor,'status':status,'response':data})
    return status,data
def ok(method,path,body=None,actor=None):
    s,d=request(method,path,body,actor); assert s in (200,201),(s,d); return d
def start(n):
    log=(run/f'application-{n}.log').open('w',encoding='utf-8')
    p=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    for _ in range(60):
        try: ok('GET','/meta/asset-types'); return p,log
        except (urllib.error.URLError,TimeoutError):
            assert p.poll() is None; time.sleep(.5) # readiness only, no domain time progression
    p.terminate(); p.wait(); log.close(); raise RuntimeError('startup timeout')
def simultaneous(call):
    gate=threading.Barrier(6)
    def worker(i): gate.wait(timeout=10); return call(i)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool: return list(pool.map(worker,range(6)))
p,log=start(1)
try:
    owner=ok('POST','/parties',{'name':'Recovery owner','kind':'PERSON'})['id']
    inspector=ok('POST','/parties',{'name':'Recovery inspector','kind':'PERSON'})['id']
    def schema(name,types):
        sid=ok('POST','/schemas',{'name':name,'assetTypes':types})['id']; sp='/schemas/'+sid
        ok('POST',sp+'/draft',{})
        criteria=[{'id':'HK','subsystem':None,'evidence':[],
            'rule':{'type':'OPTIONS','options':{'clean':{'code':'OK','result':'APPROVED','severity':None,'description':'clean'}}}}]
        if 'FACTORY' in types or 'FACILITY' in types:
            parts=['electrical installation','pressure system']+(['building safety'] if 'FACILITY' in types else [])
            criteria.extend(dict(criteria[0],id='PART'+str(i),subsystem=s) for i,s in enumerate(parts))
        ok('POST',sp+'/draft/sections',{'name':'Shared','order':1,'criteria':criteria})
        ok('POST',sp+'/publish',{}); return sid
    sid=schema('Equipment',['EQUIPMENT','LABORATORY']); sp='/schemas/'+sid
    audit_before=ok('GET','/audit?type=SCHEMA&id='+sid)
    status,_=request('POST',sp+'/publish',{})
    assert status==422 and audit_before==ok('GET','/audit?type=SCHEMA&id='+sid)
    results['F-REPEAT-03']={'repeatPublicationHTTP':status,'auditUnchanged':True}
    def asset(name): return ok('POST','/assets',{'name':name,'assetType':'EQUIPMENT','responsibleId':owner,'location':'Here','characteristics':{},'jurisdiction':'REFERENCE'})['id']
    aid=asset('Repeated workflow')
    ip='/inspections/'+ok('POST','/inspections',{'assetId':aid,'inspectorId':inspector,'expectedDate':'2026-10-10'})['id']
    ok('POST',ip+'/start',{},inspector); ok('PUT',ip+'/answers/HK',{'type':'OPTION','option':'clean'},inspector)
    closed=ok('POST',ip+'/close',{},inspector); ca=ok('GET','/audit')
    assert ok('POST',ip+'/close',{},inspector)==closed and ok('GET','/audit')==ca
    issued=ok('POST',ip+'/certificates',{}); certid=issued['certificate']['id']; ca=ok('GET','/audit')
    assert ok('POST',ip+'/certificates',{})['outcome']=='ALREADY_ISSUED' and ok('GET','/audit')==ca
    rs,_=request('POST',ip+'/certificates/renewal',{}); assert rs==422
    results['F-REPEAT-01/02']={'repeatCloseUnchanged':True,'repeatIssue':'ALREADY_ISSUED','auditUnchanged':True,'prematureRenewalHTTP':rs}
    correction_asset=asset('Repeated correction')
    cip='/inspections/'+ok('POST','/inspections',{'assetId':correction_asset,'inspectorId':inspector,'expectedDate':'2026-10-10'})['id']
    ok('POST',cip+'/start',{},inspector); ok('POST',cip+'/close',{},inspector)
    fp='/findings/'+ok('GET','/findings?inspectionId='+cip.split('/')[-1])[0]['id']
    executor=ok('POST','/parties',{'name':'Executor','kind':'PERSON'})['id']
    ok('POST',fp+'/plan',{'work':'Correct missing check','executorId':executor,'dueDate':(datetime.date.today()+datetime.timedelta(days=10)).isoformat()},owner)
    ok('POST',fp+'/execution',{'statement':'Measured and corrected','evidenceReferences':['audit://proof']},executor)
    ok('POST',fp+'/verification',{'satisfactory':True,'reason':'Checked'},inspector)
    fb=ok('GET',fp); ab=ok('GET','/audit'); ob=ok('GET','/admin/outbox')
    vs,_=request('POST',fp+'/verification',{'satisfactory':True,'reason':'Checked'},inspector)
    assert vs==422 and ok('GET',fp)==fb and ok('GET','/audit')==ab and ok('GET','/admin/outbox')==ob
    results['F-REPEAT-04']={'repeatVerificationHTTP':vs,'findingAuditOutboxUnchanged':True}
    for _ in range(2): ok('POST','/admin/jobs/run',{})
    results['F-CONCUR-03']={}
    for round_number in range(3):
        ac=asset('Concurrent '+str(round_number))
        replies=simultaneous(lambda _: request('POST','/inspections',{'assetId':ac,'inspectorId':inspector,'expectedDate':'2026-10-10'}))
        stored=ok('GET','/inspections?assetId='+ac)
        results['F-CONCUR-03'][str(round_number)]={'HTTP':[r[0] for r in replies],'storedOpen':len(stored)}
    target1=schema('Target factory',['FACTORY']); target2=schema('Target facility',['FACILITY'])
    replies=simultaneous(lambda i: request('POST',sp+'/applicability/transfer',{'targetSchemaId':target1 if i%2==0 else target2,'assetType':'EQUIPMENT'}))
    schemas=ok('GET','/schemas'); owners=[s['id'] for s in schemas if 'EQUIPMENT' in s['assetTypes']]
    assert len(owners)==1,owners
    results['F-CONCUR-04']={'HTTP':[r[0] for r in replies],'equipmentOwners':owners}
    snapshots={path:ok('GET',path) for path in [ip,ip+'/act','/certificates/'+certid,'/certificates/'+certid+'/report','/audit','/admin/outbox','/schemas']}
finally:
    p.terminate(); p.wait(timeout=15); log.close()
    (run/'http-before.json').write_text(json.dumps(transcript,indent=2),encoding='utf-8')
p,log=start(2)
try:
    equality={path:ok('GET',path)==before for path,before in snapshots.items()}
    ok('POST','/admin/jobs/run',{})
    assert ok('POST',ip+'/certificates',{})['outcome']=='ALREADY_ISSUED'
    results['F-RESTART-02']={'equality':equality,'repeatIssueAfterReopen':'ALREADY_ISSUED',
        'termination':'Windows terminate after requests completed; not graceful JVM shutdown or in-flight crash test'}
    meta['exit']=0
finally:
    p.terminate(); p.wait(timeout=15); log.close()
    meta['end']=datetime.datetime.now().astimezone().isoformat(); meta['results']=results
    (run/'metadata.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
    (run/'http.json').write_text(json.dumps(transcript,indent=2),encoding='utf-8')
print(json.dumps(results,indent=2))
