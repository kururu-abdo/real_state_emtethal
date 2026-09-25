import json
from pathlib import Path
import pytest
from app import agents
from app.models import Draft, Extraction, Evidence

class FakeModel:
    def __init__(self, result): self.result=result
    def with_structured_output(self, schema): return self
    def invoke(self, messages): return self.result


def test_rejects_fabricated_source_quote(monkeypatch):
    monkeypatch.setenv('LLM_MODE','live')
    result=Extraction(summary_ar='ملخص',summary_en='Summary',unresolved=[],
                      evidence=[Evidence(document='deed.txt',page=1,quote='invented ownership',field='owner')])
    monkeypatch.setattr(agents,'model',lambda:FakeModel(result))
    with pytest.raises(ValueError,match='cannot be located'):
        agents.extract([{'name':'deed.txt','pages':[{'page':1,'text':'Original source text'}]}])


def test_model_cannot_replace_mandatory_clauses(monkeypatch):
    monkeypatch.setenv('LLM_MODE','live')
    bundle=json.loads((Path(__file__).resolve().parents[1]/'config/clauses.demo.json').read_text())
    facts={'project_id':'DEMO-001','developer':'D','buyer':'B','unit':'A','deed_number':'D',
           'license_number':'L','price_sar':'10.00','handover_date':'2028-01-01',
           'escrow_iban':'SA0380000000608010167519'}
    candidate=Draft(facts=facts,clauses=bundle['clauses'])
    candidate.clauses[0].ar='تم تغيير النص بواسطة النموذج'
    candidate.clauses[0].en='Model silently changed a mandatory clause'
    monkeypatch.setattr(agents,'model',lambda:FakeModel(candidate))
    result=agents.draft_contract(facts,{},bundle)
    assert result.clauses[0].ar==bundle['clauses'][0]['ar']
    candidate.facts.buyer='Invented buyer'
    with pytest.raises(ValueError,match='changed structured'):
        agents.draft_contract(facts,{},bundle)
