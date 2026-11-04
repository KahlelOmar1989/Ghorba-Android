"""Build prerequisites and privacy gates. Does NOT substitute a real Android build/device test."""
from pathlib import Path
import json, re
from xml.etree import ElementTree as ET
p=Path(__file__).resolve().parent
manifest=p/'app/src/main/AndroidManifest.xml'
root=ET.parse(manifest).getroot()
ns='{http://schemas.android.com/apk/res/android}'
perms={node.attrib.get(ns+'name','') for node in root.findall('uses-permission')}
required={'android.permission.RECORD_AUDIO','android.permission.FOREGROUND_SERVICE_MICROPHONE','android.permission.INTERNET','android.permission.POST_NOTIFICATIONS'}
assert required<=perms, f'Missing permissions: {required-perms}'
for forbidden in ['android.permission.READ_SMS','android.permission.READ_CONTACTS','android.permission.MANAGE_EXTERNAL_STORAGE', 'android.permission.QUERY_ALL_PACKAGES']:
    assert forbidden not in perms, f'Unnecessary broad permission: {forbidden}'
app=root.find('application')
assert app.attrib.get(ns+'allowBackup') == 'false'
assert app.attrib.get(ns+'usesCleartextTraffic') == 'false'
services=app.findall('service')
assert any(s.attrib.get(ns+'foregroundServiceType')=='microphone' for s in services)
assert (p/'app/src/main/assets/ghorba_orb.webp').stat().st_size > 20000
source='\n'.join(f.read_text(encoding='utf-8') for f in (p/'app/src/main/java').rglob('*.java'))
assert 'ACTION_STOP' in source and 'startForeground(' in source, 'Missing microphone session controls'
assert 'confirm(' in source and 'AndroidKeyStore' in source, 'Consent or encryption missing'
assert 'isHighRiskPrivateText' in source, 'Sensitive text check missing'
assert 'AiClient.ask(' in source and 'https' in source, 'AI relay unavailable'
assert 'OPENAI_API_KEY' not in source, 'Do not ship API secrets in app'
ai=(p/'ai-relay/src/index.js').read_text(encoding='utf-8')
assert "'https://api.openai.com/v1/responses'" in ai
assert 'store:false' in ai
assert 'GHORBA_CLIENT_TOKEN' in ai
assert not re.search(r'sk-(proj-)?[a-zA-Z0-9_-]{20,}', ai), 'Possible API key in code'
data=json.loads((p/'app/src/main/assets/trades_ar_de.json').read_text(encoding='utf-8'))
assert len(data)>=28
wf=(p/'.github/workflows/android-apk.yml').read_text(encoding='utf-8')
assert 'assembleDebug' in wf and 'upload-artifact' in wf
print('PASS: release gates for consent, HTTPS, local data encryption, no broad app permissions, trade data and CI build.')
print('NOT VERIFIED: real APK compilation, Samsung installation, microphone behavior, voice gender or external AI deployment.')
