(function () {
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  document.querySelectorAll('.js-disconnect').forEach(btn => {
    btn.addEventListener('click', async () => {
      if (!confirm('이 플랫폼 연결을 해제하시겠습니까?')) return;
      const id = btn.dataset.id;
      const headers = {};
      if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
      const res = await fetch('/api/v1/social-accounts/' + id, { method: 'DELETE', headers });
      if (res.ok) location.reload();
      else alert('실패');
    });
  });
})();
