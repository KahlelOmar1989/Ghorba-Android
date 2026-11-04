"""Static smoke tests for the 1.5 trade guide; run without Android SDK."""
from pathlib import Path
from urllib.parse import urlparse
import json
root=Path(__file__).parent
assets=root/'app/src/main/assets'
data=json.loads((assets/'trades_ar_de.json').read_text(encoding='utf8'))
assert len(data)>=25
assert len({e['id'] for e in data})==len(data)
for e in data:
    assert len(e['steps'])>=5,(e['id'],'steps')
    assert len(e['tools'])>=3,(e['id'],'tools')
    assert len(e['terms'])>=3,(e['id'],'terms')
    assert len(e['safety'])>35,e['id']
    assert urlparse(e['source_url']).scheme=='https'
    assert (assets/'guides'/(e['id']+'.png')).stat().st_size>7000
src=(root/'app/src/main/java/com/ghorba/assistant/MainActivity.java').read_text()
assert 'showTradeIndex' in src and 'showTradeEntry' in src
assert 'askAi' in src and 'TradeGuide.context' in src
assert 'https://www.youtube.com/results?search_query=' in src
assert 'https://www.google.com/search?tbm=isch&q=' in src
client=(root/'app/src/main/java/com/ghorba/assistant/AiClient.java').read_text()
assert 'body.put("trade",trade)' in client
relay=(root/'ai-relay/src/index.js').read_text()
assert 'tradeContext' in relay and 'max(payload?.trade,2600)' in relay
print(f'PASS: {len(data)} professions, illustrated workflows, Arabic/German terms, reference links, video/photo search and AI context.')
