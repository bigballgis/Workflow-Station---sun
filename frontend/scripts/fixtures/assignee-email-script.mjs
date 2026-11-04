/** FU configuration script: single Lookup rows and ID selectors share the same contract. */
export const assigneeEmailBody = String.raw`var api = $inject.api;
var selected = $inject.args[0];
var source = 'assignee_id';
var target = 'assignee_email';

function userId(value) {
  if (typeof value === 'string') return value.trim();
  if (value && typeof value === 'object' && !Array.isArray(value) && typeof value.id === 'string') return value.id.trim();
  return '';
}

function emailAddress(value) {
  if (typeof value !== 'string') return '';
  var email = value.trim();
  return /^[^\s@;,]+@[^\s@;,]+\.[^\s@;,]+$/.test(email) ? email : '';
}

api.clearFieldError(target);
api.setValue(target, '');
api.updateRule(target, { info: '' });
if (selected === null || selected === undefined || selected === '') return;

var selectedId = userId(selected);
if (!selectedId) {
  api.setFieldError(target, 'Please select a valid assignee.');
  return;
}

function isCurrent() { return userId(api.getValue(source)) === selectedId; }

var lookupEmail = selected && typeof selected === 'object' ? emailAddress(selected.email) : '';
if (lookupEmail) {
  if (isCurrent()) api.setValue(target, lookupEmail);
  return;
}

api.updateRule(target, { info: 'Loading email...' });
return fetch('/api/v1/admin/users/' + encodeURIComponent(selectedId), {
  credentials: 'same-origin',
  headers: { Accept: 'application/json' },
  signal: AbortSignal.timeout(15000)
})
  .then(function(response) {
    if (!response.ok) throw new Error('User lookup failed: HTTP ' + response.status);
    return response.json();
  })
  .then(function(user) {
    if (!isCurrent()) return;
    var email = user && user.id === selectedId ? emailAddress(user.email) : '';
    if (!email) throw new Error('Selected user has no valid email.');
    api.setValue(target, email);
    api.clearFieldError(target);
    api.updateRule(target, { info: '' });
  })
  .catch(function(error) {
    if (!isCurrent()) return;
    api.setValue(target, '');
    api.updateRule(target, { info: '' });
    api.setFieldError(target, error && error.message ? error.message : 'Unable to load the assignee email.');
  });`;

export const assigneeEmailScript = '$FNX:' + assigneeEmailBody;
