'use strict';
const $ = id => document.getElementById(id);
let token = location.hash.slice(1), file = null, upload = null, online = false, pending = false;
const initialTheme = window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
document.documentElement.dataset.theme = initialTheme;
$('theme').onclick = () => { document.documentElement.dataset.theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'; };
// Keep the secret out of subsequent navigation, referrers and visible browser history.
if (token) history.replaceState(null, '', location.pathname);
function message(text = '') { $('message').textContent = text; }
function panel(id) { ['dropzone','selection','progress-panel','result'].forEach(key => $(key).hidden = key !== id); }
function selected(next) {
  if (!online || pending || upload) return;
  message();
  if (!next || !next.name.toLowerCase().endsWith('.apk') || next.size === 0 || next.size > 50 * 1024 * 1024) {
    message('Choose a complete .apk file up to 50 MiB. App bundles and split packages aren’t supported.'); return;
  }
  file = next;
  $('filename').textContent = file.name;
  $('filesize').textContent = (file.size / 1048576).toFixed(2) + ' MiB · ready for its next adventure';
  panel('selection');
}
$('choose').onclick = () => $('file').click();
$('file').onchange = () => { selected($('file').files[0]); $('file').value = ''; };
$('remove').onclick = () => { file = null; panel('dropzone'); message(); };
const zone = $('dropzone');
['dragenter','dragover'].forEach(name => zone.addEventListener(name, e => { e.preventDefault(); if (online) zone.classList.add('dragging'); }));
['dragleave','drop'].forEach(name => zone.addEventListener(name, e => { e.preventDefault(); zone.classList.remove('dragging'); }));
zone.addEventListener('drop', e => { if (e.dataTransfer.files.length !== 1) message('Choose one APK at a time.'); else selected(e.dataTransfer.files[0]); });
window.addEventListener('dragover', e => e.preventDefault());
window.addEventListener('drop', e => e.preventDefault());
function result(title, detail, success = false) {
  panel('result'); $('result-title').textContent = title; $('result-detail').textContent = detail;
  $('result-icon').textContent = success ? '✓' : '↗'; $('again').hidden = true;
}
$('send').onclick = () => {
  if (!file || !online || upload) return;
  message(); panel('progress-panel'); $('progress').value = 0; $('percentage').textContent = '0%';
  $('progress-title').textContent = 'On its way.'; $('cancel').hidden = false;
  const xhr = new XMLHttpRequest(); upload = xhr;
  xhr.open('POST', '/api/upload'); xhr.setRequestHeader('Authorization', 'Bearer ' + token);
  xhr.setRequestHeader('Content-Type', 'application/octet-stream'); xhr.timeout = 300000;
  xhr.upload.onprogress = e => {
    if (!e.lengthComputable) return;
    const percent = Math.floor(e.loaded / e.total * 100);
    $('progress').value = percent; $('percentage').textContent = percent + '%';
    if (percent === 100) { $('progress-title').textContent = 'A final check.'; $('progress-detail').textContent = 'Your watch is checking the package. Installation still needs your approval.'; }
  };
  const failed = text => { upload = null; panel('selection'); message(text); };
  xhr.onload = () => {
    upload = null;
    let data; try { data = JSON.parse(xhr.responseText); } catch { failed('The watch returned an unexpected response. Try a new session.'); return; }
    if (xhr.status !== 200) { failed(data.error || 'The file could not be transferred. Try again.'); return; }
    pending = true; file = null;
    result('Over to your wrist.', 'Review ' + data.label + ' and approve installation on your watch.'); poll();
  };
  xhr.onerror = () => failed('The connection was interrupted. Keep both devices on the same Wi-Fi and try again.');
  xhr.ontimeout = () => failed('The transfer timed out. Start a new watch session and try again.');
  xhr.onabort = () => failed('Transfer cancelled. Nothing was installed.');
  xhr.send(file);
};
$('cancel').onclick = () => upload?.abort();
$('again').onclick = () => { if (!pending) { file = null; panel('dropzone'); message(); } };
$('access-form').onsubmit = e => { e.preventDefault(); token = $('access').value.trim().replace(/^.*#/, ''); $('access').value = ''; poll(); };
let polling = false;
async function poll() {
  if (polling || !token) return;
  polling = true;
  try {
    const response = await fetch('/api/status', { headers: { Authorization: 'Bearer ' + token }, cache: 'no-store', signal: AbortSignal.timeout ? AbortSignal.timeout(5000) : undefined });
    if (response.status === 403) {
      online = false; $('access-form').hidden = false; $('choose').disabled = true;
      $('connection').textContent = 'Private link needed'; message('Use the current QR code or private session key shown on your watch.'); return;
    }
    if (!response.ok) throw new Error('disconnected');
    const data = await response.json();
    if (!online) message();
    online = true; $('send').disabled = false; $('access-form').hidden = true; $('connection').textContent = 'Connected to your watch';
    $('expiry').textContent = '· ' + Math.floor(data.expiresIn / 60) + ':' + String(data.expiresIn % 60).padStart(2, '0') + ' left';
    $('choose').disabled = false;
    if (upload) return;
    const waiting = data.state === 'waiting';
    if (!waiting) {
      pending = true;
      const states = {
        ready: ['Over to your wrist.', 'Review ' + data.label + ' and tap Install on your watch.'],
        confirm: ['Your watch. Your call.', 'Continue to the system approval screen on your watch.'],
        installing: ['One last step.', 'Follow the system installer on your watch.'],
        installed: ['Looks good on you.', data.label + ' is installed. Open it on your watch.'],
        failed: ['A small detour.', data.detail]
      };
      const content = states[data.state] || ['Check your watch.', data.detail];
      result(content[0], content[1], data.state === 'installed');
    } else if (pending) {
      pending = false; result('Ready for another?', 'Your watch is ready to receive a new app.', true); $('again').hidden = false;
    }
  } catch {
    online = false; $('send').disabled = true; $('connection').textContent = 'Watch connection paused'; $('choose').disabled = true;
    if (!upload) message('Keep your watch on Wi-Fi. If the session ended, start a new one and open its new link.');
  } finally { polling = false; }
}
if (!token) { $('access-form').hidden = false; $('choose').disabled = true; $('connection').textContent = 'Private link needed'; }
poll();
setInterval(() => { if (!document.hidden) poll(); }, 1500);
document.addEventListener('visibilitychange', () => { if (!document.hidden) poll(); });
