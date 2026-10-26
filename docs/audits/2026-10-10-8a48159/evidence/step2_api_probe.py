"""Narrow Step 2 HTTP/H2 reproduction; run from repository root."""
import datetime, hashlib, json, pathlib, subprocess, sys, time, urllib.request, urllib.error

run = pathlib.Path('target/audit-evidence/2026-10-10-8a48159/' + (sys.argv[1] if len(sys.argv) > 1 else '013-step2-api'))
run.mkdir(parents=True, exist_ok=False)
jar = pathlib.Path('app/target/certiflow-app-1.0.0-SNAPSHOT-exec.jar')
command = ['C:/Program Files/Java/jdk-26.0.2/bin/java.exe', '-jar', str(jar),
           '--server.port=18082', '--spring.datasource.url=jdbc:h2:file:./' + run.as_posix() + '/db/certiflow',
           '--certiflow.jobs.enabled=false']
transcript = []
def request(method, path, body=None, actor=None):
    headers = {'Content-Type': 'application/json'}
    if actor: headers['X-Actor-Id'] = actor
    req = urllib.request.Request('http://127.0.0.1:18082/api' + path,
          data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=5) as reply: status, data = reply.status, json.load(reply)
    except urllib.error.HTTPError as e: status, data = e.code, json.load(e)
    transcript.append(dict(method=method, path=path, request=body, actor=actor, status=status, response=data))
    return status, data
def ok(method, path, body=None, actor=None):
    status, data = request(method, path, body, actor)
    assert status in (200, 201), (status, data)
    return data
metadata = {'start': datetime.datetime.now().astimezone().isoformat(), 'command': command,
            'jarSHA256': hashlib.sha256(jar.read_bytes()).hexdigest()}
with (run / 'application.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT,
                               creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        for attempt in range(50):
            try: ok('GET', '/meta/asset-types'); break
            except (urllib.error.URLError, TimeoutError):
                assert process.poll() is None, 'application exited'
                time.sleep(0.5)
        else: raise RuntimeError('startup timeout')
        owner = ok('POST', '/parties', {'name': 'Audit owner', 'kind': 'PERSON'})['id']
        inspector = ok('POST', '/parties', {'name': 'Audit inspector', 'kind': 'PERSON'})['id']
        schema = ok('POST', '/schemas', {'name': 'Audit facility', 'assetTypes': ['FACILITY']})['id']
        ok('POST', '/schemas/' + schema + '/draft', {})
        criteria = []
        for criterion, subsystem in [('ELEC', 'electrical installation'), ('PRES', 'pressure system'), ('SAFE', 'building safety')]:
            criteria.append({'id': criterion, 'subsystem': subsystem, 'evidence': [],
                'rule': {'type': 'OPTIONS', 'options': {'clean': {'code': 'OK', 'result': 'APPROVED',
                                                          'severity': None, 'description': 'clean'}}}})
        ok('POST', '/schemas/' + schema + '/draft/sections', {'name': 'Parts', 'order': 1, 'criteria': criteria})
        ok('POST', '/schemas/' + schema + '/publish', {})
        asset = ok('POST', '/assets', {'name': 'Audit annex', 'assetType': 'FACILITY', 'responsibleId': owner,
                   'location': 'Here', 'characteristics': {}, 'subsystems': ['electrical installation'], 'jurisdiction': 'REFERENCE'})['id']
        iid = ok('POST', '/inspections', {'assetId': asset, 'inspectorId': inspector, 'expectedDate': '2026-10-10'})['id']
        path = '/inspections/' + iid
        ok('POST', path + '/start', {}, inspector)
        ok('PUT', path + '/answers/ELEC', {'type': 'OPTION', 'option': 'clean'}, inspector)
        before = ok('POST', path + '/close', {}, inspector)
        audit_before = ok('GET', '/audit?type=INSPECTION&id=' + iid)
        body = {'reason': 'Absent pressure criterion', 'corrections': [{'type': 'ANSWER', 'criterionId': 'PRES',
                                                                      'answer': {'type': 'OPTION', 'option': 'clean'}}]}
        status, error = request('POST', path + '/rectifications', body, inspector)
        after = ok('GET', path)
        audit_after = ok('GET', '/audit?type=INSPECTION&id=' + iid)
        assert status == 500 and error['code'] == 'INTERNAL_ERROR', (status, error)
        assert before == after and audit_before == audit_after, 'rollback did not preserve original'
        metadata['result'] = {'scenario': 'C-RECT-04', 'HTTP': status, 'code': error['code'],
                              'inspectionUnchanged': True, 'auditUnchanged': True}
        print(json.dumps(metadata['result']))
        generic = ok('POST', '/schemas', {'name': 'Generic', 'assetTypes': ['LABORATORY']})['id']
        sp = '/schemas/' + generic
        ok('POST', sp + '/draft', {})
        common = dict(criteria[0], id='HK', subsystem=None)
        ok('POST', sp + '/draft/sections', {'name': 'Common', 'order': 1, 'criteria': [common]})
        ok('POST', sp + '/publish', {})
        ok('POST', sp + '/draft', {})
        ok('POST', sp + '/draft/sections', {'name': 'Future parts', 'order': 2, 'criteria': criteria})
        future = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=10)).isoformat()
        ok('POST', sp + '/publish', {'effectiveFrom': future})
        ok('POST', sp + '/applicability', {'assetType': 'FACTORY'})
        factory = ok('POST', '/assets', {'name': 'Factory', 'assetType': 'FACTORY', 'responsibleId': owner,
                     'location': 'Here', 'characteristics': {}, 'jurisdiction': 'REFERENCE'})
        fi = ok('POST', '/inspections', {'assetId': factory['id'], 'inspectorId': inspector, 'expectedDate': '2026-10-10'})['id']
        fp = '/inspections/' + fi
        started = ok('POST', fp + '/start', {}, inspector)
        ok('PUT', fp + '/answers/HK', {'type': 'OPTION', 'option': 'clean'}, inspector)
        ok('POST', fp + '/close', {}, inspector)
        issued = ok('POST', fp + '/certificates', {})
        certificate = ok('GET', '/certificates/' + issued['certificate']['id'])
        assert len(factory['subsystems']) == 2 and started['subsystems'] == [], (factory, started)
        assert certificate['scope'] == 'GLOBAL' and certificate['status'] == 'VALID', certificate
        metadata['coverageResult'] = {'scenario': 'C-F1-F2-01', 'assetParts': factory['subsystems'],
            'selectedVersion': started['schemaVersion'], 'inspectionParts': started['subsystems'],
            'certificateScope': certificate['scope'], 'certificateStatus': certificate['status']}
        print(json.dumps(metadata['coverageResult']))
        metadata['exit'] = 0
    finally:
        process.terminate()
        process.wait(timeout=15)
        metadata['end'] = datetime.datetime.now().astimezone().isoformat()
        (run / 'metadata.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
        (run / 'http.json').write_text(json.dumps(transcript, indent=2), encoding='utf-8')
