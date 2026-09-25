from html import escape
from weasyprint import HTML


def render_pdf(state):
    draft = state['draft']
    rows = ''.join(f'<tr><th>{escape(k)}</th><td>{escape(str(v))}</td></tr>'
                   for k, v in draft['facts'].items())
    clauses = ''.join(
        f'<section><h3>{escape(c["id"])}</h3><table class="clause"><tr>'
        f'<td lang="en">{escape(c["en"])}</td>'
        f'<td lang="ar" dir="rtl">{escape(c["ar"])}</td></tr></table>'
        f'<p class="source">Reference: {escape(c["source_reference"])}</p></section>'
        for c in draft['clauses'])
    label = 'DEMONSTRATION - NOT FOR SIGNATURE' if state['demo'] else 'Human-reviewed agreement - signature copy'
    html = f'''<!doctype html><html><head><meta charset="utf-8"><style>
    @page {{ size: A4; margin: 18mm; @bottom-center {{ content: counter(page) " / " counter(pages); }} }}
    body {{ font-family: "DejaVu Sans", sans-serif; font-size: 10pt; color: #172b36; }}
    h1 {{ color: #145b50; font-size: 21pt; }} h3 {{ font-size: 12pt; }}
    table {{ width:100%; border-collapse:collapse; table-layout:fixed; }}
    td,th {{ padding:7px; border-bottom:1px solid #ddd; text-align:left; overflow-wrap:anywhere; }}
    .clause td {{ width:50%; vertical-align:top; white-space:pre-wrap; }}
    td[dir=rtl] {{ text-align:right; }} section {{ break-inside: auto; }}
    h3 {{ break-after:avoid; }} .source {{ font-size:8pt; color:#52636b; }}
    .hash {{ font-size:7pt; overflow-wrap:anywhere; }}
    </style></head><body><h1>EMTETHAL | امتثال</h1><p>{label}</p>
    <h2 dir="rtl">عقد بيع على الخارطة</h2><h2>Off-plan sale agreement</h2>
    <table>{rows}</table>{clauses}
    <p>Reviewer: {escape(state['reviewer'])} | {escape(state['approved_at'])}</p>
    <p>Legal bundle: {escape(state['bundle_version'])}</p>
    <p class="hash">Approved content SHA-256: {escape(state['approved_hash'])}</p>
    <p>Developer signature: ____________________</p><p>Buyer signature: ____________________</p>
    </body></html>'''
    # No user HTML or external resource loading is permitted.
    return HTML(string=html, url_fetcher=lambda *a, **k: (_ for _ in ()).throw(ValueError('External resources disabled'))).write_pdf()
