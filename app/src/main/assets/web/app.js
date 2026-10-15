'use strict';
const $ = (id) => document.getElementById(id);
let token = location.hash.slice(1),
  file = null,
  upload = null,
  online = false,
  pending = false;
const initialTheme = window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
document.documentElement.dataset.theme = initialTheme;
$('theme').onclick = () => {
  document.documentElement.dataset.theme =
    document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
};
// Keep the secret out of subsequent navigation, referrers and visible browser history.
if (token) history.replaceState(null, '', location.pathname);
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
  ['dropzone', 'selection', 'progress-panel', 'result'].forEach(
    (key) => ($(key).hidden = key !== id),
  );
}
function selected(next) {
  if (!online || pending || upload) return;
  message();
  if (
    !next ||
    !next.name.toLowerCase().endsWith('.apk') ||
    next.size === 0 ||
    next.size > 50 * 1024 * 1024
  ) {
    message('Choose a single APK up to 50 MB. Bundles and split APKs won’t work here.');
    return;
  }
  file = next;
  $('filename').textContent = file.name;
  $('filesize').textContent = (file.size / 1048576).toFixed(1) + ' MB · ready to send';
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
  xhr.setRequestHeader('Authorization', 'Bearer ' + token);
  xhr.setRequestHeader('Content-Type', 'application/octet-stream');
  xhr.timeout = 300000;
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
    file = null;
    result('Made it to your watch', 'Review ' + data.label + ' on your watch.');
    poll();
  };
  xhr.onerror = () =>
    failed('Connection lost. Check that both devices are on the same Wi-Fi, then try again.');
  xhr.ontimeout = () =>
    failed('That took too long. Open a fresh link from your watch and try again.');
  xhr.onabort = () => failed('Transfer cancelled. Your app may have arrived—check your watch.');
  xhr.send(file);
};
$('cancel').onclick = () => upload?.abort();
$('again').onclick = () => {
  if (!pending) {
    file = null;
    panel('dropzone');
    message();
  }
};
$('access-form').onsubmit = (e) => {
  e.preventDefault();
  token = $('access').value.trim().replace(/^.*#/, '');
  $('access').value = '';
  poll();
};
let polling = false;
async function poll() {
  if (polling || !token) return;
  polling = true;
  try {
    const response = await fetch('/api/status', {
      headers: { Authorization: 'Bearer ' + token },
      cache: 'no-store',
      signal: AbortSignal.timeout ? AbortSignal.timeout(5000) : undefined,
    });
    if (response.status === 403) {
      online = false;
      document.querySelector('.connection').dataset.online = 'false';
      $('access-form').hidden = false;
      $('choose').disabled = true;
      $('connection').textContent = 'Let’s connect';
      message('Scan the code on your watch to reconnect.');
      return;
    }
    if (!response.ok) throw new Error('disconnected');
    const data = await response.json();
    if (!online) message();
    online = true;
    $('send').disabled = false;
    $('access-form').hidden = true;
    setText('connection', 'Connected to your watch');
    document.querySelector('.connection').dataset.online = 'true';
    $('expiry').textContent =
      '· ' +
      Math.floor(data.expiresIn / 60) +
      ':' +
      String(data.expiresIn % 60).padStart(2, '0') +
      ' remaining';
    $('choose').disabled = false;
    if (upload) return;
    const waiting = data.state === 'waiting';
    if (!waiting) {
      pending = true;
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
      if (data.state === 'failed') $('result-icon').textContent = '!';
    } else if (pending) {
      pending = false;
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
        'Check your watch’s Wi-Fi. If receiving has stopped, start again and scan the new code.',
      );
  } finally {
    polling = false;
  }
}
if (!token) {
  $('access-form').hidden = false;
  $('choose').disabled = true;
  $('connection').textContent = 'Let’s connect';
}
poll();
setInterval(() => {
  if (!document.hidden) poll();
}, 1500);
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) poll();
});
