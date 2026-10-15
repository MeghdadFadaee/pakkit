#!/usr/bin/env python3
"""Audit the built release bundle and its merged manifest; this is not Play approval."""
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
bundle = ROOT / 'app/build/outputs/bundle/release/app-release.aab'
manifest = ROOT / 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml'
root = ET.parse(manifest).getroot()
app = root.find('application')
assert root.get('package') == 'meghdad.pakkit'
assert root.find('uses-sdk').get(ANDROID + 'targetSdkVersion') == '36'
assert root.find('uses-sdk').get(ANDROID + 'minSdkVersion') == '31'
assert app.get(ANDROID + 'allowBackup') == 'false'
assert app.get(ANDROID + 'debuggable', 'false') == 'false'
assert any(e.get(ANDROID + 'name') == 'android.hardware.type.watch' and e.get(ANDROID + 'required') == 'true' for e in root.findall('uses-feature'))
assert any(e.get(ANDROID + 'name') == 'com.google.android.wearable.standalone' and e.get(ANDROID + 'value') == 'true' for e in app.findall('meta-data'))
permissions = {e.get(ANDROID + 'name') for e in root.findall('uses-permission')}
expected = {'android.permission.' + p for p in ['REQUEST_INSTALL_PACKAGES', 'POST_NOTIFICATIONS', 'INTERNET', 'ACCESS_NETWORK_STATE', 'CHANGE_NETWORK_STATE', 'WAKE_LOCK', 'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_CONNECTED_DEVICE']}
assert permissions == expected | {'meghdad.pakkit.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}, permissions
for component in app:
    if component.tag not in {'activity', 'receiver', 'service', 'provider'}:
        continue
    name = component.get(ANDROID + 'name')
    exported = component.get(ANDROID + 'exported') == 'true'
    if name.endswith('.WatchActivity'):
        assert exported and component.get(ANDROID + 'taskAffinity') == ''
    elif name == 'androidx.profileinstaller.ProfileInstallReceiver':
        assert exported and component.get(ANDROID + 'permission') == 'android.permission.DUMP'
    else:
        assert not exported, name
    if component.tag == 'service':
        assert component.get(ANDROID + 'foregroundServiceType') == 'connectedDevice'
assert root.find('queries') is None
with zipfile.ZipFile(bundle) as archive:
    names = archive.namelist()
    assets = sorted(n.removeprefix('base/assets/') for n in names if n.startswith('base/assets/') and not n.endswith('/'))
    assert assets == ['legal/licenses.txt', 'legal/privacy.txt', 'web/app.js', 'web/index.html', 'web/style.css'], assets
    for name in assets:
        assert archive.read('base/assets/' + name) == (ROOT / 'app/src/main/assets' / name).read_bytes(), name
    assert not any(n.endswith(('.apk', '.jks', '.keystore')) for n in names)
    native = [n for n in names if n.endswith('.so')]
    assert not native, 'Reassess 64-bit and 16 KB page support after adding native libraries'
    signed = any(n.upper().startswith('META-INF/') and n.upper().endswith(('.RSA', '.EC', '.DSA')) for n in names)
lint_results = {}
for variant in ['debug', 'release']:
    lint = ET.parse(ROOT / f'app/build/reports/lint-results-{variant}.xml').getroot()
    issues = lint.findall('issue')
    assert not any(i.get('severity') in {'Error', 'Fatal'} for i in issues)
    lint_results[variant] = [i.get('id') for i in issues if i.get('severity') == 'Warning']
report = {
    'bundle': str(bundle.relative_to(ROOT)),
    'sha256': hashlib.sha256(bundle.read_bytes()).hexdigest(),
    'bytes': bundle.stat().st_size,
    'mergedManifestChecked': True,
    'targetSdk': 36,
    'minSdk': 31,
    'assetsMatchSource': assets,
    'nativeLibraries': native,
    'signaturePresent': signed,
    'signatureCryptographicallyVerified': False,
    'lintErrors': 0,
    'lintWarnings': lint_results,
    'publicationGates': ['Verify upload signing and enroll in Play App Signing', 'Hosted privacy policy and real support contact', 'Store artwork and Console declarations', 'Hardware and browser compatibility matrix', 'Restricted-permission, foreground-service and Wear quality review', 'Confirm transport and Data safety handling against policy'],
}
output = ROOT / 'app/build/reports/validation/release-audit.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
