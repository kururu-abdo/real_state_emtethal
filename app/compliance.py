import hashlib
import json
import re
from .models import Draft


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                     separators=(',', ':')).encode()).hexdigest()


def normalize_iban(value):
    return re.sub(r'\s+', '', value).upper()


def valid_iban(value):
    value = normalize_iban(value)
    if not re.fullmatch(r'SA\d{22}', value):
        return False
    return int(''.join(str(ord(c)-55) if c.isalpha() else c
                       for c in value[4:] + value[:4])) % 97 == 1


def validate_approval(draft: Draft, registry: dict, original: dict, bundle: dict):
    facts = draft.facts.model_dump(mode='json')
    if facts['project_id'] != original['project_id']:
        raise ValueError('Project identity cannot be changed during review')
    project = registry.get(facts['project_id'])
    if not project or not project.get('verified') or not project.get('evidence_reference'):
        raise ValueError('A verified project escrow registry entry is required')
    iban = normalize_iban(facts['escrow_iban'])
    if not valid_iban(iban) or iban != normalize_iban(project['escrow_iban']):
        raise ValueError('Escrow IBAN is invalid or does not match the project registry')
    if facts['license_number'] != project['license_number']:
        raise ValueError('Project license does not match the registry')
    if not bundle.get('approved_by') or not bundle.get('version'):
        raise ValueError('Legal clause bundle has not been approved')
    if any('REVIEW_REQUIRED' in c.ar or 'REVIEW_REQUIRED' in c.en or
           'REVIEW_REQUIRED' in c.source_reference for c in draft.clauses):
        raise ValueError('Resolve every REVIEW_REQUIRED clause before approval')
