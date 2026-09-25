from typing import TypedDict
from langgraph.graph import StateGraph, START, END
from langgraph.types import interrupt
from .agents import extract, draft_contract
from .compliance import digest, validate_approval
from .models import Draft

class State(TypedDict, total=False):
    request: dict
    documents: list
    extraction: dict
    draft: dict
    status: str
    revision: int
    demo: bool
    bundle: dict
    registry: dict
    bundle_version: str
    reviewer: str
    approved_at: str
    approved_hash: str
    approval: dict


def build_graph(checkpointer):
    def context(state):
        return {'extraction': extract(state['documents']).model_dump(mode='json')}

    def draft(state):
        result = draft_contract(state['request']['facts'], state['extraction'], state['bundle'])
        return {'draft': result.model_dump(mode='json'), 'revision': 1, 'status': 'awaiting_review'}

    def review(state):
        decision = interrupt({'type': 'contract_review', 'revision': state['revision'],
                              'draft': state['draft'], 'extraction': state['extraction']})
        if decision['decision'] == 'reject':
            return {'status': 'rejected', 'approval': decision, 'reviewer': decision['reviewer']}
        corrected = Draft.model_validate(decision['draft'])
        validate_approval(corrected, state['registry'], state['request']['facts'], state['bundle'])
        data = corrected.model_dump(mode='json')
        return {'draft': data, 'status': 'approved', 'reviewer': decision['reviewer'],
                'approved_at': decision['approved_at'], 'approval': decision,
                'approved_hash': digest(data)}

    def finalize(state):
        validate_approval(Draft.model_validate(state['draft']), state['registry'],
                          state['request']['facts'], state['bundle'])
        if digest(state['draft']) != state['approved_hash']:
            raise ValueError('Approved content integrity failure')
        return {'status': 'finalized_demo' if state['demo'] else 'finalized'}

    graph = StateGraph(State)
    graph.add_node('bilingual_context', context)
    graph.add_node('legal_drafter', draft)
    graph.add_node('human_review', review)
    graph.add_node('escrow_finalization', finalize)
    graph.add_edge(START, 'bilingual_context')
    graph.add_edge('bilingual_context', 'legal_drafter')
    graph.add_edge('legal_drafter', 'human_review')
    graph.add_conditional_edges('human_review', lambda s: 'done' if s['status'] == 'rejected' else 'final',
                                {'done': END, 'final': 'escrow_finalization'})
    graph.add_edge('escrow_finalization', END)
    return graph.compile(checkpointer=checkpointer)
