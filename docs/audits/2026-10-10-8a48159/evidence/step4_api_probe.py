"""Review-only baseline-JAR HTTP/file-H2 workflows; no writes to application sources.

python .../step4_api_probe.py <unique-run-id> packaged|controlled
The controlled variant extracts baseline classes and replaces only the Clock port.
"""
import datetime as dt, hashlib, json, pathlib, re, subprocess, sys, time, urllib.request, urllib.error, zipfile

ROOT = pathlib.Path.cwd()
EVIDENCE = ROOT / 'docs/audits/2026-10-10-8a48159/evidence'
RUN = ROOT / 'target/audit-evidence/2026-10-10-8a48159' / sys.argv[1]
MODE = sys.argv[2]
RUN.mkdir(parents=True, exist_ok=False)
JAR = ROOT / 'target/audit-evidence/2026-10-10-8a48159/014-step3-tests/baseline-exec.jar'
JAVA = pathlib.Path('C:/Program Files/Java/jdk-26.0.2/bin')
PORT = 18084 if MODE == 'packaged' else 18085
BASE = f'http://127.0.0.1:{PORT}'
requests, results, processes = [], {}, []
meta = {'start': dt.datetime.now().astimezone().isoformat(), 'mode': MODE,
        'head': subprocess.check_output(['git','rev-parse','HEAD'], text=True).strip(),
        'jarSHA256': hashlib.sha256(JAR.read_bytes()).hexdigest(), 'commands': []}

def command(args):
    meta['commands'].append(args)
    r = subprocess.run(args, capture_output=True, text=True)
    (RUN / ('compile-'+str(len(meta['commands']))+'.log')).write_text(r.stdout+r.stderr, encoding='utf-8')
    assert r.returncode == 0, r.stdout+r.stderr

def request(method, path, body=None, actor=None, status=None):
    headers = {'Content-Type':'application/json'}
    if actor: headers['X-Actor-Id'] = actor
    req = urllib.request.Request(BASE+'/api'+path, method=method, headers=headers,
        data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=15) as r: code, data = r.status, json.load(r)
    except urllib.error.HTTPError as e: code, data = e.code, json.load(e)
    requests.append(dict(method=method,path=path,body=body,actor=actor,status=code,response=data))
    if status is None: assert code in (200,201), (path,code,data)
    else: assert code == status, (path,code,data)
    return data

def now(value):
    temporary = RUN/'clock.tmp'
    temporary.write_text(value, encoding='utf-8')
    temporary.replace(RUN/'clock.txt')

def semantic(value, key=None):
    # These published fields represent sets, whose JSON order can change in a new JVM.
    # Keep every other array ordered, especially audit history, criteria and provenance.
    if isinstance(value, dict): return {k:semantic(v,k) for k,v in value.items()}
    if isinstance(value, list):
        values=[semantic(v) for v in value]
        return sorted(values,key=lambda x:json.dumps(x,sort_keys=True)) if key in {
            'blockingSeverities','restrictions','coveredSubsystems'} else values
    return value

def start(number, revision=1):
    config = [f'--server.port={PORT}', '--spring.datasource.url=jdbc:h2:file:./'+
        RUN.relative_to(ROOT).as_posix()+'/db/certiflow', '--certiflow.jobs.enabled=false']
    if MODE == 'packaged':
        args = [str(JAVA/'java.exe'), '-javaagent:'+str(RUN/'shutdown-agent.jar'), '-jar', str(JAR), *config]
    else:
        ready = RUN/f'ready-{number}.txt'
        args = [str(JAVA/'java.exe'), '-Daudit.clock='+str(RUN/'clock.txt'),
            '-Daudit.ready='+str(ready), '-Daudit.policyRevision='+str(revision),
            '-cp', classpath, str(EVIDENCE/'AuditControlledApp.java'), *config]
    meta['commands'].append(args)
    log = (RUN/f'application-{number}.log').open('w', encoding='utf-8')
    p = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
        text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    processes.append((p,log))
    for _ in range(100):
        assert p.poll() is None, 'startup failed; inspect application log'
        try:
            if MODE == 'controlled' and not ready.exists(): time.sleep(.3); continue
            request('GET','/meta/asset-types'); return p, log
        except urllib.error.URLError: time.sleep(.3) # readiness only, never advancing business time
    raise AssertionError('startup timeout')

def stop(p, log):
    p.stdin.write('shutdown\n'); p.stdin.flush()
    code = p.wait(timeout=30); p.stdin.close(); log.close()
    meta.setdefault('processExits', []).append(code)
    assert code == 0, code

def outcome(result, severity=None):
    return dict(code=result,result=result,severity=severity,description=result.lower())

def criterion(cid, subsystem=None, evidence=None):
    return dict(id=cid,subsystem=subsystem,evidence=evidence or [],rule=dict(type='OPTIONS',options={
        'clean':outcome('APPROVED'),'untidy':outcome('OBSERVED','MEDIUM'),
        'hazardous':outcome('REJECTED','HIGH')}))

def schema(name, types, criteria):
    sid = request('POST','/schemas',dict(name=name,assetTypes=types))['id']
    publish(sid,criteria)
    return sid

def publish(sid, criteria, at=None):
    sp = '/schemas/'+sid
    request('POST',sp+'/draft',{})
    for section in request('GET',sp)['draft']['sections']:
        request('DELETE',sp+'/draft/sections/'+urllib.parse.quote(section['name']))
    request('POST',sp+'/draft/sections',dict(name='Audit checks',order=1,criteria=criteria))
    return request('POST',sp+'/publish',{} if at is None else dict(effectiveFrom=at))

def asset(name, kind='EQUIPMENT', jurisdiction='REFERENCE'):
    return request('POST','/assets',dict(name=name,assetType=kind,responsibleId=owner,
        location='Audit laboratory',characteristics={},jurisdiction=jurisdiction))['id']

def assign(aid):
    return '/inspections/'+request('POST','/inspections',dict(assetId=aid,inspectorId=inspector,
        expectedDate='2026-10-11'))['id']

def inspect(aid, answers):
    ip = assign(aid); request('POST',ip+'/start',{},inspector)
    for cid, answer in answers.items(): request('PUT',ip+'/answers/'+cid,dict(type='OPTION',option=answer),inspector)
    request('POST',ip+'/close',{},inspector)
    return ip

def plan_all(ip, due='2026-10-20'):
    findings = request('GET','/findings?inspectionId='+ip.split('/')[-1])
    for f in findings:
        request('POST','/findings/'+f['id']+'/plan',dict(work='Restore conformity',executorId=executor,dueDate=due),owner)
    return findings

def verify(fid):
    fp='/findings/'+fid
    request('POST',fp+'/execution',dict(statement='Corrected and checked',evidenceReferences=['audit://execution']),executor)
    request('POST',fp+'/verification',dict(satisfactory=True,reason='Independent audit check'),inspector)

try:
    if MODE == 'packaged':
        (RUN/'classes').mkdir()
        command([str(JAVA/'javac.exe'),'-d',str(RUN/'classes'),str(EVIDENCE/'AuditShutdownAgent.java')])
        (RUN/'manifest.txt').write_text('Premain-Class: AuditShutdownAgent\n\n',encoding='utf-8')
        command([str(JAVA/'jar.exe'),'cfm',str(RUN/'shutdown-agent.jar'),str(RUN/'manifest.txt'),
            '-C',str(RUN/'classes'),'.'])
    else:
        with zipfile.ZipFile(JAR) as z:
            for name in z.namelist():
                if name.startswith(('BOOT-INF/classes/','BOOT-INF/lib/')): z.extract(name,RUN/'baseline')
        classpath = str(RUN/'baseline/BOOT-INF/classes')+';'+str(RUN/'baseline/BOOT-INF/lib/*')
        now('2026-10-11T15:00:00Z')
    p,log = start(1)
    owner=request('POST','/parties',dict(name='Audit owner',kind='ORGANIZATION'))['id']
    inspector=request('POST','/parties',dict(name='Audit inspector',kind='PERSON'))['id']
    executor=request('POST','/parties',dict(name='Audit executor',kind='PERSON'))['id']
    sid=schema('Audit equipment',['EQUIPMENT'],[criterion('HK')])
    parts=['electrical installation','pressure system','building safety']
    fsid=schema('Audit facility',['FACILITY'],[criterion('SHARED')]+[criterion('P'+str(n),part) for n,part in enumerate(parts)])

    # All required report types and progressive evidence/notes, then auditable correction.
    ls=schema('Audit laboratory',['LABORATORY'],[criterion('DOC',evidence=[dict(type='DOCUMENT',label='manual',mandatory=True,minimumCount=1)])])
    lab=asset('Document-backed laboratory','LABORATORY'); lip=assign(lab)
    request('POST',lip+'/start',{},inspector)
    request('PUT',lip+'/answers/DOC',dict(type='OPTION',option='hazardous'),inspector)
    request('POST',lip+'/evidence',dict(criterionId='DOC',requirementLabel='manual',reference='audit://manual'),inspector)
    request('POST',lip+'/notes',dict(criterionId='DOC',text='Original observation'),inspector)
    request('POST',lip+'/close',{},inspector)
    act_before=request('GET',lip+'/act')
    request('POST',lip+'/rectifications',dict(reason='Correct mistaken option',corrections=[
        dict(type='ANSWER',criterionId='DOC',answer=dict(type='OPTION',option='clean'))]),inspector)
    act_after=request('GET',lip+'/act')
    assert act_before != act_after and 'Correct mistaken option' in json.dumps(act_after)
    request('GET',lip+'/findings-summary')
    lab_cert=request('POST',lip+'/certificates',{})['certificate']
    request('GET','/certificates/'+lab_cert['id']+'/report')
    results['E-API-01']={'reports':3,'rectificationRetained':True,'labInspection':lip}

    # Same result, differing jurisdiction decisions; resolve the conditional obligation.
    bip=inspect(asset('Buenos Aires observation',jurisdiction='AR-BA'),{'HK':'untidy'})
    cip=inspect(asset('Cordoba observation',jurisdiction='AR-CBA'),{'HK':'untidy'})
    bf=plan_all(bip); plan_all(cip)
    bc=request('POST',bip+'/certificates',{})['certificate']; assert bc['mode']=='CONDITIONAL'
    blocked=request('POST',cip+'/certificates',{},status=422)
    assert blocked['outcome']=='BLOCKED' and 'ConditionalNotAllowed' in json.dumps(blocked)
    assert request('GET','/certificates?inspectionId='+cip.split('/')[-1])==[]
    verify(bf[0]['id']); assert not request('GET','/findings/'+bf[0]['id'])['pendingNonConformity']
    assert request('GET','/certificates/'+bc['id'])['status']=='VALID'
    results['E-F3-01']={'BA':bc['mode'],'CBA':blocked['outcome'],'verifiedConditionalStatus':'VALID'}

    # F1/F3 mixed results, scope isolation, shared criterion and derived global.
    fa=asset('Mixed parts','FACILITY','AR-BA')
    fip=inspect(fa,{'SHARED':'clean','P0':'clean','P1':'untidy','P2':'hazardous'})
    ff=plan_all(fip)
    pc0=request('POST',fip+'/certificates',dict(subsystem=parts[0]))['certificate']
    pc1=request('POST',fip+'/certificates',dict(subsystem=parts[1]))['certificate']
    assert pc0['mode']=='REGULAR' and pc1['mode']=='CONDITIONAL'
    request('POST',fip+'/certificates',dict(subsystem=parts[2]),status=422)
    assert request('GET',fip+'/global-derivation')['type']=='NotDerivable'
    request('GET',fip+'/eligibility',status=422) # backend path of AUD-003
    for f in ff: verify(f['id'])
    pc2=request('POST',fip+'/certificates',dict(subsystem=parts[2]))['certificate']
    derived=request('GET',fip+'/global-derivation'); assert derived['type']=='Derived'
    assert len(request('GET','/certificates?inspectionId='+fip.split('/')[-1]))==3
    results['E-F1-01']={'modes':[pc0['mode'],pc1['mode'],pc2['mode']],
        'derivedAfterVerification':True,'persistedOnlyPartials':True,'inspection':fip}
    sharedip=inspect(asset('Shared rejected parts','FACILITY','AR-BA'),
        {'SHARED':'hazardous','P0':'clean','P1':'clean','P2':'clean'})
    for part in parts: request('POST',sharedip+'/certificates',dict(subsystem=part),status=422)
    results['E-F1-02']={'sharedFailureBlocksAll':True}

    # Invalid paths: assert actual API error and persisted aggregate on refusal.
    invalid_a=asset('Invalid-request fixture'); invalidip=assign(invalid_a)
    for actor in [None,'nonexistent-actor']:
        before=request('GET',invalidip); audits=request('GET','/audit')
        error=request('POST',invalidip+'/start',{},actor,status=401)
        assert error['code']=='ACTOR_REQUIRED' and before==request('GET',invalidip) and audits==request('GET','/audit')
    request('GET','/inspections/missing',status=404)
    request('GET','/admin/outbox?status=INVALID',status=400)
    request('POST',invalidip+'/close',{},inspector,status=422)
    request('POST',invalidip+'/start',{},inspector)
    before=request('GET',invalidip)
    request('PUT',invalidip+'/answers/HK',dict(type='UNKNOWN'),inspector,status=400)
    assert before==request('GET',invalidip)
    request('POST',invalidip+'/close',{},inspector) # backend closure with zero answers, AUD-004
    request('PUT',invalidip+'/answers/HK',dict(type='OPTION',option='clean'),inspector,status=422)
    repeat=request('POST',lip+'/certificates',{}); assert repeat['outcome']=='ALREADY_ISSUED'
    request('POST',lip+'/certificates/renewal',{},status=422)
    results['E-ERROR-01']={'actorHTTP':401,'missingHTTP':404,'invalidHTTP':400,'illegalHTTP':422,
        'zeroAnswerCloseHTTP':200,'repeatIssue':repeat['outcome'],'refusedInputStoredUnchanged':True}

    # F2 query precision and startup retention: no sleeping for business boundaries.
    future='2026-10-12T15:00:00Z' if MODE=='controlled' else '2099-01-01T00:00:00Z'
    v1=request('GET','/schemas/'+sid)['versions'][0]
    future_asset=asset('Future schema frozen inspection'); oldip=assign(future_asset)
    request('POST',oldip+'/start',{},inspector)
    v2=publish(sid,[criterion('NEW')],future)
    instant=dt.datetime.fromisoformat(future.replace('Z','+00:00'))
    minus=(instant-dt.timedelta(seconds=1)).strftime('%Y-%m-%dT%H:%M:%S')+'.999999999Z'
    plus=instant.strftime('%Y-%m-%dT%H:%M:%S')+'.000000001Z'
    lookup=[request('GET','/schemas/'+sid+'/effective-version?at='+x)['number'] for x in [minus,future,plus]]
    assert lookup==[1,2,2]
    assert request('GET',oldip)['schemaVersion']==v1['id']
    request('GET','/schemas/'+sid+'/effective-version?at=bad',status=400)
    request('GET','/schemas/'+sid+'/effective-version?at=2000-01-01T00:00:00Z',status=404)
    results['E-F2-01']={'lookupMinusExactPlus':lookup,'oldInspection':oldip,'v1':v1['id'],'v2':v2['id']}
    if MODE=='controlled':
        for label,at,expected in [('minus',minus,1),('exact',future,2),('plus',plus,2)]:
            now(at); ip=assign(asset('Startup '+label)); started=request('POST',ip+'/start',{},inspector)
            assert started['schemaVersion']==(v1 if expected==1 else v2)['id']
        assert request('GET',oldip)['schemaVersion']==v1['id']
        results['E-F2-02']={'startupMinusExactPlus':[1,2,2],'frozenRetained':True}
        # Conditional expiration suspends only its part; recovery restores derivation.
        now('2026-10-21T15:00:00Z')
        # The original mixed fixture is already verified; make a still-open observation.
        expip=inspect(asset('Expiration isolation','FACILITY','AR-BA'),
            {'SHARED':'clean','P0':'clean','P1':'untidy','P2':'clean'})
        expf=plan_all(expip,'2026-10-22')
        expcs=[request('POST',expip+'/certificates',dict(subsystem=part))['certificate'] for part in parts]
        now('2026-10-23T15:00:00Z'); sweep=request('POST','/admin/jobs/run',{})
        statuses=[request('GET','/certificates/'+c['id'])['status'] for c in expcs]
        assert statuses==['VALID','SUSPENDED','VALID'],statuses
        assert request('GET',expip+'/global-derivation')['type']=='NotDerivable'
        verify(expf[0]['id'])
        assert request('GET','/certificates/'+expcs[1]['id'])['status']=='VALID'
        assert request('GET',expip+'/global-derivation')['type']=='Derived'
        results['E-ACTION-02']={'sweep':sweep,'partStatusesAfterExpiry':statuses,'reactivatedAfterVerification':True}
        # Reproduce AUD-009 through HTTP/H2 while UTC has crossed midnight but local date has not.
        zoneip=inspect(asset('Inclusive local deadline','FACILITY','REFERENCE'),
            {'SHARED':'clean','P0':'clean','P1':'untidy','P2':'clean'})
        zonef=plan_all(zoneip,'2026-10-23')[0]
        now('2026-10-24T00:30:00Z') # October 23, 21:30 Buenos Aires
        zone_assessment=request('GET',zoneip+'/eligibility?subsystem='+urllib.parse.quote(parts[1]))
        assert 'OverdueOpenAction' in json.dumps(zone_assessment),zone_assessment
        request('POST','/admin/jobs/run',{})
        action=request('GET','/findings/'+zonef['id'])['correctiveActions'][0]
        assert action['status']=='PLANNED' and not action['deadlineBreached'],action
        results['E-TIME-01']={'instant':'2026-10-24T00:30:00Z','localDate':'2026-10-23',
            'eligibilityOverdue':True,'actionStatusAfterSweep':action['status'],
            'deadlineBreached':action['deadlineBreached'],'finding':'AUD-009'}

    # Package static resource delivery is distinct from browser usability.
    with urllib.request.urlopen(BASE+'/') as r: markup=r.read().decode(); assert r.status==200
    scripts=re.findall(r'<script[^>]*src="([^"]+)"',markup)
    assert scripts
    for script in scripts:
        with urllib.request.urlopen(BASE+script) as r: assert r.status==200 and len(r.read())>100
    results['E-PACKAGE-01']={'rootHTTP':200,'scriptsHTTP':200,'scripts':scripts,'browserExecuted':False}
    request('POST','/admin/jobs/run',{})
    assert request('GET','/admin/outbox?status=PENDING')==[]
    assert request('POST','/admin/outbox/retry-dead',{})=={'revived':0,'delivered':0}
    paths=[lip,lip+'/act',lip+'/findings-summary',bip,fip,fip+'/global-derivation',oldip,
        '/certificates','/certificates/'+bc['id']+'/report','/findings','/audit','/schemas','/admin/outbox']
    snapshots={path:request('GET',path) for path in paths}
    stop(p,log)
    p,log=start(2,2 if MODE=='controlled' else 1)
    equality={path:semantic(request('GET',path))==semantic(before) for path,before in snapshots.items()}
    assert all(equality.values()),equality
    # Continue frozen old-version inspection after restart despite newer scheduling/configuration.
    request('PUT',oldip+'/answers/HK',dict(type='OPTION',option='clean'),inspector)
    request('POST',oldip+'/close',{},inspector)
    assert request('GET',oldip)['schemaVersion']==v1['id']
    assert request('POST',lip+'/certificates',{})['outcome']=='ALREADY_ISSUED'
    results['E-RESTART-01']={'equalSnapshots':equality,'continuedOldVersion':True,'shutdown':'JVM shutdown hooks / Spring context.close'}
    if MODE=='controlled':
        assessment=request('GET',bip+'/eligibility')
        assert '"revision": 2' in json.dumps(assessment)
        assert semantic(request('GET','/certificates/'+bc['id']+'/report'))==semantic(snapshots['/certificates/'+bc['id']+'/report'])
        results['E-POLICY-01']={'activeRevision':2,'historicalCertificateReportRetained':True}
        # Successful HTTP renewal exactly at certificate expiry, with a new inspection.
        now(lab_cert['expiresAt'])
        jobs=request('POST','/admin/jobs/run',{})
        assert request('GET','/certificates/'+lab_cert['id'])['status']=='EXPIRED'
        nip=assign(lab); request('POST',nip+'/start',{},inspector)
        request('PUT',nip+'/answers/DOC',dict(type='OPTION',option='clean'),inspector)
        request('POST',nip+'/evidence',dict(criterionId='DOC',requirementLabel='manual',reference='audit://renewal-manual'),inspector)
        request('POST',nip+'/close',{},inspector)
        renewed=request('POST',nip+'/certificates/renewal',{})['certificate']
        assert lab_cert['id'] in json.dumps(renewed['previousCertificateId'])
        audits=request('GET','/audit'); certs=request('GET','/certificates')
        assert request('POST',nip+'/certificates/renewal',{})['outcome']=='ALREADY_ISSUED'
        assert audits==request('GET','/audit') and certs==request('GET','/certificates')
        results['E-RENEW-01']={'expiredExactlyAtBoundary':True,'sweep':jobs,'renewHTTP':201,
            'predecessorRetained':True,'repeatHTTP':200,'repeatAuditCertificatesUnchanged':True}
    stop(p,log)
    meta['exit']=0
except BaseException as error:
    meta['exit']=1; meta['error']=repr(error)
    raise
finally:
    for proc, logfile in processes:
        if proc.poll() is None:
            try: stop(proc,logfile)
            except Exception:
                proc.terminate(); proc.wait(timeout=15); logfile.close()
                meta.setdefault('cleanupLimitations',[]).append('forced termination after harness error')
    meta['end']=dt.datetime.now().astimezone().isoformat(); meta['results']=results
    (RUN/'metadata.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
    (RUN/'http.json').write_text(json.dumps(requests,indent=2),encoding='utf-8')
print(json.dumps(results,indent=2))
