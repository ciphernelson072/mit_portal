document.addEventListener('DOMContentLoaded', () => {
  const dialog = document.getElementById('photoViewerDialog');
  const preview = document.getElementById('photoViewerImage');
  const caption = document.getElementById('photoViewerCaption');
  if (!dialog || !preview || !caption) return;

  function openPhoto(image) {
    if (!image?.src || image.src.startsWith('data:image/svg+xml')) return;
    preview.src = image.src;
    preview.alt = image.alt || 'Profile photo';
    caption.textContent = image.dataset.photoCaption || image.alt || 'Profile photo';
    if (!dialog.open) dialog.showModal();
    dialog.querySelector('[data-photo-viewer-close]')?.focus();
  }

  document.addEventListener('click', event => {
    const image = event.target.closest('img[data-photo-viewer]');
    if (image) openPhoto(image);
    if (event.target === dialog) dialog.close();
  });

  document.addEventListener('keydown', event => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const image = event.target.closest?.('img[data-photo-viewer]');
    if (!image) return;
    event.preventDefault();
    openPhoto(image);
  });

  dialog.querySelector('[data-photo-viewer-close]')?.addEventListener('click', () => dialog.close());
  dialog.addEventListener('close', () => {
    preview.removeAttribute('src');
    caption.textContent = '';
  });
});
