'use strict';
const $ = (id) => document.getElementById(id);
let file = null,
  upload = null,
  online = false,
  pending = false,
  choosingNext = false;
// Storage is isolated by scheme + host + port. No cookie can leak access to another
// app's server on a different port of the same watch. Nothing is copied by the user.
let credential = '';
function accessHeaders() {
  try {
    credential = localStorage.getItem('pakkitAccess') || credential;
  } catch {}
  return credential ? { Authorization: 'Bearer ' + credential } : {};
}
const initialTheme = window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
document.documentElement.dataset.theme = initialTheme;
$('theme').onclick = () => {
  document.documentElement.dataset.theme =
    document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
};
function setText(id, value) {
  if ($(id).textContent !== value) $(id).textContent = value;
}
function message(text = '') {
  setText('message', text);
}
function panel(id) {
  const step = id === 'progress-panel' ? 1 : id === 'result' ? 2 : 0;
  document.querySelectorAll('.steps li').forEach((item, index) => {
    if (index === step) item.setAttribute('aria-current', 'step');
    else item.removeAttribute('aria-current');
    item.classList.toggle('complete', index < step);
  });
  $('progress-panel').setAttribute('aria-busy', String(id === 'progress-panel'));
  ['pairing', 'dropzone', 'selection', 'progress-panel', 'result'].forEach(
    (key) => ($(key).hidden = key !== id),
  );
}
function selected(next) {
  if (!online || pending || upload) return;
  message();
  if (!next || next.size === 0) {
    message('This file is empty. Choose an app to send.');
    return;
  }
  file = next;
  $('filename').textContent = file.name;
  $('filesize').dataset.bytes = String(file.size);
  $('filesize').textContent = formatSize(file.size) + ' · ready to send';
  panel('selection');
  $('send').focus({ preventScroll: true });
}
$('choose').onclick = () => $('file').click();
$('file').onchange = () => {
  selected($('file').files[0]);
  $('file').value = '';
};
$('remove').onclick = () => {
  file = null;
  panel('dropzone');
  message();
  $('choose').focus({ preventScroll: true });
};
const zone = $('dropzone');
['dragenter', 'dragover'].forEach((name) =>
  zone.addEventListener(name, (e) => {
    e.preventDefault();
    if (online) zone.classList.add('dragging');
  }),
);
['dragleave', 'drop'].forEach((name) =>
  zone.addEventListener(name, (e) => {
    e.preventDefault();
    zone.classList.remove('dragging');
  }),
);
zone.addEventListener('drop', (e) => {
  if (e.dataTransfer.files.length !== 1) message('Choose one APK at a time.');
  else selected(e.dataTransfer.files[0]);
});
window.addEventListener('dragover', (e) => e.preventDefault());
window.addEventListener('drop', (e) => e.preventDefault());
function result(title, detail, success = false) {
  panel('result');
  setText('result-title', title);
  setText('result-detail', detail);
  $('result-icon').textContent = success ? '✓' : '↗';
  $('result').dataset.state = success ? 'success' : 'waiting';
  $('again').hidden = true;
}
$('send').onclick = () => {
  if (!file || !online || upload) return;
  message();
  panel('progress-panel');
  $('progress').value = 0;
  $('percentage').textContent = '0%';
  $('progress-title').textContent = 'On its way';
  $('progress-detail').textContent = 'Keep this page open. Your app is on its way.';
  $('cancel').hidden = false;
  $('cancel').focus({ preventScroll: true });
  const xhr = new XMLHttpRequest();
  upload = xhr;
  xhr.open('POST', '/api/upload');
  Object.entries(accessHeaders()).forEach(([name, value]) => xhr.setRequestHeader(name, value));
  xhr.setRequestHeader('Content-Type', 'application/octet-stream');
  // No total upload timeout. A slow, progressing transfer is allowed to finish.
  xhr.upload.onprogress = (e) => {
    if (!e.lengthComputable) return;
    const percent = Math.floor((e.loaded / e.total) * 100);
    $('progress').value = percent;
    $('percentage').textContent = percent + '%';
    if (percent === 100) {
      $('progress-title').textContent = 'Just checking…';
      $('progress-detail').textContent = 'Your watch is getting your app ready.';
    }
  };
  const failed = (text) => {
    upload = null;
    panel('selection');
    message(text);
    $('send').focus({ preventScroll: true });
  };
  xhr.onload = () => {
    upload = null;
    let data;
    try {
      data = JSON.parse(xhr.responseText);
    } catch {
      failed('We couldn’t finish the transfer. Open a fresh link from your watch.');
      return;
    }
    if (xhr.status !== 200) {
      failed(data.error || 'Your app didn’t arrive. Try sending it again.');
      return;
    }
    pending = true;
    choosingNext = false;
    file = null;
    result('Made it to your watch', 'Review ' + data.label + ' on your watch.');
    poll();
  };
  xhr.onerror = () =>
    failed('Connection lost. Your file is ready to send again when your watch reconnects.');
  xhr.onabort = () => failed('Transfer cancelled. Your app may have arrived—check your watch.');
  xhr.send(file);
};
$('cancel').onclick = () => upload?.abort();
$('again').onclick = () => {
  if (!pending) {
    file = null;
    choosingNext = true;
    panel('dropzone');
    message();
  }
};
function formatSize(bytes) {
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const unit = Math.min(Math.floor(Math.log10(Math.max(bytes, 1)) / 3), units.length - 1);
  return (
    new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(bytes / 1000 ** unit) +
    ' ' +
    units[unit]
  );
}
function pairing(state) {
  online = false;
  document.querySelector('.connection').dataset.online = 'false';
  setText('connection', state === 'denied' ? 'Not connected' : 'Waiting for your watch');
  $('choose').disabled = true;
  $('send').disabled = true;
  panel('pairing');
  setText('pairing-title', state === 'denied' ? 'Maybe next time?' : 'A quick hello');
  setText(
    'pairing-detail',
    state === 'denied'
      ? 'Your watch declined this browser.'
      : state === 'busy'
        ? 'Your watch is connecting another browser. We’ll wait here.'
        : 'Tap Allow browser on your watch. You only need to do this once while receiving is on.',
  );
  $('retry-connection').hidden = state !== 'denied';
}
async function connect(retry = false) {
  const response = await fetch(retry ? '/api/retry' : '/api/connect', {
    method: 'POST',
    headers: accessHeaders(),
  });
  if (!response.ok) throw new Error('disconnected');
  const data = await response.json();
  if (data.credential) {
    credential = data.credential;
    try {
      localStorage.setItem('pakkitAccess', credential);
    } catch {}
  }
  pairing(data.connection);
}
$('retry-connection').onclick = () => {
  connect(true).catch(() => message('Open Pakkit on your watch and start receiving.'));
};
let polling = false;
async function poll() {
  if (polling) return;
  polling = true;
  try {
    const response = await fetch('/api/status', {
      headers: accessHeaders(),
      cache: 'no-store',
      signal: AbortSignal.timeout ? AbortSignal.timeout(5000) : undefined,
    });
    if (response.status === 403) {
      const data = await response.json();
      if (data.connection === 'unknown') await connect();
      else pairing(data.connection);
      return;
    }
    if (!response.ok) throw new Error('disconnected');
    const data = await response.json();
    if (!online) {
      message();
      if (!pending && !upload) panel(file ? 'selection' : 'dropzone');
    }
    online = true;
    $('send').disabled = false;
    setText('connection', 'Connected to your watch');
    document.querySelector('.connection').dataset.online = 'true';
    $('choose').disabled = false;
    if (upload) return;
    const waiting = data.state === 'waiting';
    if (!waiting) {
      if (['installed', 'failed'].includes(data.state) && choosingNext) return;
      pending = !['installed', 'failed'].includes(data.state);
      const states = {
        ready: ['Made it to your watch', 'Review ' + data.label + ' on your watch.'],
        confirm: ['One tap closer', 'Approve installation on your watch.'],
        installing: ['Getting ready', 'Your watch will ask you to approve installation.'],
        installed: ['Looks good on you.', data.label + ' is ready. Enjoy it on your watch.'],
        failed: ['Let’s try that again', data.detail],
      };
      const content = states[data.state] || ['Check your watch.', data.detail];
      result(content[0], content[1], data.state === 'installed');
      $('result').dataset.state = data.state;
      if (['installed', 'failed'].includes(data.state)) $('again').hidden = false;
      if (data.state === 'failed') $('result-icon').textContent = '!';
    } else if (pending) {
      ((pending = false), (choosingNext = false));
      result('Ready for another?', 'Your watch is ready to receive a new app.', true);
      $('again').hidden = false;
    }
  } catch {
    online = false;
    document.querySelector('.connection').dataset.online = 'false';
    $('send').disabled = true;
    setText('connection', 'Watch out of reach');
    $('choose').disabled = true;
    if (!upload)
      message(
        'Check Wi-Fi on your watch. If its address has changed, open the new one shown in Pakkit.',
      );
  } finally {
    polling = false;
  }
}
panel('pairing');
poll();
setInterval(() => {
  if (!document.hidden) poll();
}, 1500);
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) poll();
});
