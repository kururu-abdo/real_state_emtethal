from datetime import date
from decimal import Decimal
from typing import Annotated, Literal
from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

Text = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=12000)]
Short = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=250)]
REQUIRED = {'parties', 'property', 'price_payment', 'handover', 'delay_compensation',
            'escrow', 'termination_refunds', 'defects_warranty', 'disputes', 'disclosures'}

class Strict(BaseModel):
    model_config = ConfigDict(extra='forbid')

class Clause(Strict):
    id: Short
    ar: Text
    en: Text
    source_reference: Text

class Facts(Strict):
    project_id: Short
    developer: Short
    buyer: Short
    unit: Short
    deed_number: Short
    license_number: Short
    price_sar: Decimal = Field(gt=0, max_digits=15, decimal_places=2)
    handover_date: date
    escrow_iban: Short

class Draft(Strict):
    facts: Facts
    clauses: list[Clause] = Field(min_length=10, max_length=50)

    @model_validator(mode='after')
    def required_clauses(self):
        ids = [c.id for c in self.clauses]
        if len(ids) != len(set(ids)) or not REQUIRED.issubset(ids):
            raise ValueError('Required clause categories missing or duplicate IDs')
        return self

class Evidence(Strict):
    document: Short
    page: int = Field(ge=1)
    quote: Text
    field: Short

class Extraction(Strict):
    summary_ar: Text
    summary_en: Text
    evidence: list[Evidence]
    unresolved: list[Text]

class Start(Strict):
    facts: Facts
    document_ids: list[str] = Field(min_length=1, max_length=12)
    framework: Literal['wafi_sale'] = 'wafi_sale'

class Approval(Strict):
    expected_revision: int = Field(ge=1)
    draft: Draft
    decision: Literal['approve', 'reject']
    note: Text
    legal_reviewed: bool = False
    bilingual_equivalence_verified: bool = False
    source_documents_verified: bool = False
    escrow_evidence_reference: Text
