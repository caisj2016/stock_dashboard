function setupDashboardLazyLoad() {
  const tasks = [
    { id: 'aiTrumpPanel', run: () => typeof lazyLoadTrumpNews === 'function' && lazyLoadTrumpNews() },
    { id: 'nikkeiDigestPanel', run: () => typeof lazyLoadTopicDigest === 'function' && lazyLoadTopicDigest('nikkei') },
    { id: 'semiDigestPanel', run: () => typeof lazyLoadTopicDigest === 'function' && lazyLoadTopicDigest('semiconductor') },
  ];

  if (!('IntersectionObserver' in window)) {
    tasks.forEach(task => task.run());
    return;
  }

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      const task = tasks.find(item => item.id === entry.target.id);
      if (!task) return;
      task.run();
      observer.unobserve(entry.target);
    });
  }, { rootMargin: '280px 0px' });

  tasks.forEach(task => {
    const el = document.getElementById(task.id);
    if (el) observer.observe(el);
  });
}

function refreshTopicDigestIfLoaded(topic) {
  if (window.DigestState?.cache?.[topic]) {
    loadTopicDigest(topic, { force: true });
    return;
  }
  if (typeof lazyLoadTopicDigest === 'function') {
    lazyLoadTopicDigest(topic);
  }
}

function refreshTrumpIfLoaded() {
  if (window.NewsState?.trumpLoaded) {
    loadTrumpNews(true);
    return;
  }
  if (typeof lazyLoadTrumpNews === 'function') {
    lazyLoadTrumpNews();
  }
}

async function initApp() {
  AppState.trumpTimer = setInterval(refreshTrumpIfLoaded, 10 * 60 * 1000);
  await refresh();
  setupDashboardLazyLoad();
  setInterval(() => refreshTopicDigestIfLoaded('nikkei'), 10 * 60 * 1000);
  setInterval(() => refreshTopicDigestIfLoaded('semiconductor'), 10 * 60 * 1000);
}

initApp();
