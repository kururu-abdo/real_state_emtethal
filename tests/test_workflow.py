import copy
from concurrent.futures import ThreadPoolExecutor
import pytest
from fastapi.testclient import TestClient
from app.main import create_app
from app.compliance import valid_iban

ANALYST = {'Authorization': 'Bearer ' + 'a'*32}
OFFICER = {'Authorization': 'Bearer ' + 'b'*32}
FACTS = {'project_id':'DEMO-001','developer':'Demo developer','buyer':'Demo buyer','unit':'A-101',
         'deed_number':'DEMO-DEED','license_number':'DEMO-LICENSE','price_sar':'950000.00',
         'handover_date':'2028-06-30','escrow_iban':'SA0380000000608010167519'}

@pytest.fixture
def setup(tmp_path, monkeypatch):
    monkeypatch.setenv('ANALYST_API_KEY', 'a'*32)
    monkeypatch.setenv('REVIEWER_API_KEY', 'b'*32)
    monkeypatch.setenv('LLM_MODE', 'demo')
    return tmp_path


def start(client):
    doc = client.post('/api/documents',headers=ANALYST,
                      files={'file':('deed.txt','مشروع تجريبي\nProject DEMO-001; delivery 2028-06-30','text/plain')})
    assert doc.status_code == 201, doc.text
    result = client.post('/api/contracts',headers=ANALYST,
                         json={'facts':FACTS,'document_ids':[doc.json()['id']]})
    assert result.status_code == 201, result.text
    return result.json()


def approval(run):
    draft=copy.deepcopy(run['draft'])
    for clause in draft['clauses']:
        clause['en']='Synthetic test wording. This agreement is only a demonstration.'
        clause['ar']='نص تجريبي للاختبار فقط. هذا المستند ليس عقداً للاستخدام الفعلي.'
        clause['source_reference']='Synthetic test fixture, not a legal authority'
    return {'expected_revision':run['revision'],'draft':draft,'decision':'approve',
            'note':'Synthetic test approval','legal_reviewed':True,
            'bilingual_equivalence_verified':True,'source_documents_verified':True,
            'escrow_evidence_reference':'Synthetic registry reference'}


def test_restart_review_pdf_and_replay(setup):
    with TestClient(create_app(setup)) as client:
        run=start(client)
        assert run['waiting'] and run['status']=='awaiting_review'
        assert client.get('/api/contracts/'+run['id']+'/pdf',headers=ANALYST).status_code==409
    with TestClient(create_app(setup)) as client:
        assert client.get('/api/contracts/'+run['id'],headers=OFFICER).json()['waiting']
        body=approval(run)
        url='/api/contracts/'+run['id']+'/review'
        assert client.post(url,headers=ANALYST,json=body).status_code==403
        response=client.post(url,headers=OFFICER,json=body)
        assert response.status_code==200,response.text
        assert response.json()['status']=='finalized_demo'
        assert response.json()['draft']==body['draft']
        assert client.post(url,headers=OFFICER,json=body).status_code==409
        pdf=client.get('/api/contracts/'+run['id']+'/pdf',headers=OFFICER)
        assert pdf.status_code==200 and pdf.content.startswith(b'%PDF')
        (setup/'sample.pdf').write_bytes(pdf.content)
        assert client.get('/api/contracts/'+run['id']+'/audit',headers=OFFICER).json()['checkpoint_approval']['reviewer']=='compliance-officer'

@pytest.mark.parametrize('case',['iban','license','project','attestation','placeholder','revision','missing_clause'])
def test_invalid_approval_keeps_interrupt(setup,case):
    with TestClient(create_app(setup)) as client:
        run=start(client);body=approval(run)
        if case=='iban':body['draft']['facts']['escrow_iban']='SA0080000000608010167519'
        if case=='license':body['draft']['facts']['license_number']='WRONG'
        if case=='project':body['draft']['facts']['project_id']='OTHER'
        if case=='attestation':body['legal_reviewed']=False
        if case=='placeholder':body['draft']['clauses'][0]['ar']='REVIEW_REQUIRED'
        if case=='revision':body['expected_revision']=99
        if case=='missing_clause':body['draft']['clauses'].pop()
        response=client.post('/api/contracts/'+run['id']+'/review',headers=OFFICER,json=body)
        assert response.status_code in (409,422),response.text
        assert client.get('/api/contracts/'+run['id'],headers=OFFICER).json()['waiting']


def test_rejection_and_unknown_access(setup):
    with TestClient(create_app(setup)) as client:
        assert client.get('/api/contracts').status_code==401
        assert client.get('/api/contracts/missing',headers=OFFICER).status_code==404
        run=start(client);body=approval(run);body['decision']='reject'
        r=client.post('/api/contracts/'+run['id']+'/review',headers=OFFICER,json=body)
        assert r.json()['status']=='rejected'
        assert client.get('/api/contracts/'+run['id']+'/pdf',headers=OFFICER).status_code==409


def test_concurrent_approvals(setup):
    with TestClient(create_app(setup)) as client:
        run=start(client);body=approval(run)
        def send(_):return client.post('/api/contracts/'+run['id']+'/review',headers=OFFICER,json=body).status_code
        with ThreadPoolExecutor(2) as pool:
            assert sorted(pool.map(send,range(2)))==[200,409]


def test_upload_validation_and_iban(setup):
    assert valid_iban(FACTS['escrow_iban'])
    assert not valid_iban('SA'+'0'*22)
    with TestClient(create_app(setup)) as client:
        assert client.post('/api/documents',headers=ANALYST,files={'file':('x.txt',b'')}).status_code==422
        assert client.post('/api/documents',headers=ANALYST,files={'file':('x.exe',b'data')}).status_code==422
        assert client.post('/api/contracts',headers=ANALYST,json={'facts':FACTS,'document_ids':['missing']}).status_code==422
