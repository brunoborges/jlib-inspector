// Simple dark/light toggle persisted in localStorage
(() => {
  const key = 'jlib-theme';
  const btn = document.getElementById('themeToggle');
  const apply = (m) => { document.body.classList.remove('theme-dark','theme-light'); document.body.classList.add(m); };
  const stored = localStorage.getItem(key) || 'theme-dark';
  apply(stored);
  if (btn) {
    btn.addEventListener('click', () => {
      const next = document.body.classList.contains('theme-dark') ? 'theme-light' : 'theme-dark';
      apply(next); localStorage.setItem(key, next);
    });
  }
})();

// Simple lightbox for screenshots
(() => {
  const gallery = document.querySelector('[data-gallery]');
  const lb = document.getElementById('lightbox');
  if (!gallery || !lb) return;
  const imgEl = lb.querySelector('img');
  const capEl = lb.querySelector('.caption');
  const closeBtn = lb.querySelector('.close');
  const open = (src, cap) => { imgEl.src = src; capEl.textContent = cap || ''; lb.hidden = false; document.body.style.overflow='hidden'; };
  const close = () => { lb.hidden = true; imgEl.src=''; document.body.style.overflow=''; };
  gallery.addEventListener('click', e => {
    const t = e.target.closest('img');
    if (!t) return;
    open(t.dataset.full || t.src, t.closest('figure')?.querySelector('figcaption')?.textContent);
  });
  closeBtn?.addEventListener('click', close);
  lb.addEventListener('click', e => { if (e.target === lb) close(); });
  window.addEventListener('keydown', e => { if (e.key === 'Escape' && !lb.hidden) close(); });
})();
