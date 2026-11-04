from pathlib import Path
from xml.etree import ElementTree as ET
r=Path(__file__).parent
ET.parse(r/'app/src/main/AndroidManifest.xml')
ET.parse(r/'app/src/main/res/values/styles.xml')
sources=list((r/'app/src/main/java').rglob('*.java'))
assert len(sources)>=5
manifest=(r/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
assert 'READ_CONTACTS' not in manifest and 'READ_SMS' not in manifest
assert 'RECORD_AUDIO' in manifest
text='\n'.join(p.read_text(encoding='utf-8') for p in sources)
checks=['AES/GCM/NoPadding','AndroidKeyStore','SpeechRecognizer','TextToSpeech','DatePickerDialog','POST_NOTIFICATIONS','ACTION_OPEN_DOCUMENT','ACTION_PICK','AlertDialog.Builder','setAndAllowWhileIdle','ghorba_orb.webp']
for check in checks:
    assert check in text, f'Feature missing: {check}'
assert (r/'app/src/main/assets/ghorba_orb.webp').stat().st_size>20000
print('PASS: XML parse, requested permissions, privacy measures, key feature code and image asset present.')
print('NOTICE: Android SDK missing; compilation and runtime testing were NOT performed.')
