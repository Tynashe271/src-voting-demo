// Confirmation dialogs for destructive/irreversible admin actions live here, not as inline
// onsubmit="" attributes, so the CSP's script-src can drop 'unsafe-inline' (see
// SecurityHeadersMiddleware).
document.addEventListener('submit', (event) => {
  const form = event.target.closest('form[data-confirm]');
  if (form && !window.confirm(form.dataset.confirm)) {
    event.preventDefault();
  }
});
