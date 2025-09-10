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
