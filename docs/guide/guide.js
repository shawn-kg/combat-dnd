/* Shared behaviour for every page of the guide: theme, glossary, code blocks, copy buttons,
   checklists, quizzes and reading progress. Every saved thing is a convenience: the pages work without it. */
{
  const store = {
    get(k, d) { try { const v = localStorage.getItem(k); return v === null ? d : JSON.parse(v) } catch { return d } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)) } catch { /* storage blocked: fine */ } },
  }
  const REPO = 'https://github.com/shawn-kg/combat-dnd/blob/main/'
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c])

  /* ---------- theme: same as the computer, light, dark ---------- */
  const THEMES = ['system', 'light', 'dark']
  const LABEL = { system: 'Auto', light: 'Light', dark: 'Dark' }
  const applyTheme = (t) => { if (t === 'system') delete document.documentElement.dataset.theme; else document.documentElement.dataset.theme = t }
  let theme = store.get('cdnd-guide-theme', 'system')
  if (!THEMES.includes(theme)) theme = 'system'
  if (theme !== 'system') applyTheme(theme)
  for (const b of document.querySelectorAll('[data-theme-toggle]')) {
    const show = () => { b.textContent = `Theme: ${LABEL[theme]}`; b.title = `Theme: ${theme === 'system' ? 'same as this computer' : theme} (click to change)` }
    show()
    b.addEventListener('click', () => { theme = THEMES[(THEMES.indexOf(theme) + 1) % 3]; applyTheme(theme); store.set('cdnd-guide-theme', theme); show() })
  }

  /* ---------- glossary tooltips ---------- */
  const GLOSSARY = {
    'event-sourcing': ['Event sourcing', 'Storing what happened (a list of events) instead of the current values. The current state is computed by replaying the events in order.'],
    'event': ['Event', 'A fact in the past tense, like AttackLogged or TurnChanged. Events are never changed or deleted; undo marks them undone.'],
    'command': ['Command', 'A request to change something, like LogAttack or NextTurn. The server may accept it (producing events) or refuse it.'],
    'optimistic': ['Optimistic concurrency', 'Instead of locking a record while someone edits it, every change says which version it was based on. If the version moved on in the meantime, the change is refused (HTTP 409) and the person retries.'],
    'tls': ['TLS termination', 'Decrypting HTTPS at the edge. Render’s proxy holds the certificate, decrypts the request, and forwards plain HTTP to our container inside its network.'],
    'sse': ['Server-Sent Events (SSE)', 'A long-lived HTTP response that the server keeps writing messages to. One-way (server → browser), plain HTTP, and the browser reconnects by itself.'],
    'csrf': ['CSRF', 'Cross-site request forgery: another website making your browser send a request to ours, with your cookies attached. Blocked here by requiring a custom header other sites can’t add.'],
    'dto': ['DTO', 'Data transfer object: a class shaped exactly like the JSON sent over the wire, kept separate from the internal model so each can change on its own.'],
    'transaction': ['Transaction', 'A group of database changes that all happen or none do. If anything fails halfway, Postgres rolls everything back.'],
    'virtual-threads': ['Virtual threads', 'Java 21’s lightweight threads. Each request gets one; thousands can wait (for example on open streams) without the cost of thousands of operating-system threads.'],
    'pure': ['Pure function', 'A function whose result depends only on its inputs and which changes nothing else: no database, no clock, no network. Easy to test and reason about.'],
    'spa': ['Single-page app', 'One HTML page whose JavaScript draws every screen and changes the address bar itself, instead of the server sending a new page per link.'],
    'csv-injection': ['CSV injection', 'A spreadsheet treats a cell starting with = + - or @ as a formula. A name like =HYPERLINK(…) could run when the file is opened, so such cells are prefixed with a quote.'],
    'jsonb': ['jsonb', 'Postgres’s binary JSON column type: stores JSON documents and can still query inside them.'],
  }
  let tip = null
  const hideTip = () => { tip?.remove(); tip = null }
  const showTip = (el) => {
    hideTip()
    const g = GLOSSARY[el.dataset.g]
    if (!g) return
    tip = document.createElement('div')
    tip.className = 'tip'
    tip.setAttribute('role', 'tooltip')
    tip.innerHTML = `<b>${esc(g[0])}</b>${esc(g[1])}`
    document.body.append(tip)
    const r = el.getBoundingClientRect()
    const w = tip.offsetWidth
    const left = Math.min(Math.max(8, r.left + window.scrollX), window.scrollX + document.documentElement.clientWidth - w - 8)
    tip.style.left = `${left}px`
    tip.style.top = `${r.bottom + window.scrollY + 8}px`
  }
  // Also used for content a page draws later (bindGlossary(thatElement)).
  const bindGlossary = (root) => {
    for (const d of root.querySelectorAll('dfn[data-g]')) {
      if (d.dataset.bound) continue
      d.dataset.bound = '1'
      d.tabIndex = 0
      d.addEventListener('mouseenter', () => showTip(d))
      d.addEventListener('mouseleave', hideTip)
      d.addEventListener('focus', () => showTip(d))
      d.addEventListener('blur', hideTip)
      d.addEventListener('click', (e) => { e.stopPropagation(); tip ? hideTip() : showTip(d) })
    }
  }
  bindGlossary(document)
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') hideTip() })
  document.addEventListener('click', hideTip)

  /* ---------- code blocks: <figure class="code" data-file="path" data-hl="2,3-4"><pre>…</pre></figure> ---------- */
  const lines = (spec) => {
    const set = new Set()
    for (const part of (spec || '').split(',').filter(Boolean)) {
      const [a, b] = part.split('-').map(Number)
      for (let i = a; i <= (b || a); i++) set.add(i)
    }
    return set
  }
  // Code can come from <script type="text/plain" id="…"> (data-src), so it needs no HTML escaping.
  const renderCode = (fig) => {
    let pre = fig.querySelector('pre')
    if (!pre) { pre = document.createElement('pre'); fig.prepend(pre) }
    const src = fig.dataset.src && document.getElementById(fig.dataset.src)
    const text = (src ? src.textContent : pre.textContent).replace(/^\n/, '').replace(/\s+$/, '')
    const hl = lines(fig.dataset.hl)
    pre.innerHTML = text.split('\n').map((l, i) => {
      const comment = /^\s*(\/\/|#|--|\/\*|\*)/.test(l)
      return `<span class="ln${hl.has(i + 1) ? ' hl' : ''}">${comment ? `<span class="c">${esc(l)}</span>` : esc(l) || ' '}</span>`
    }).join('')
    if (fig.dataset.file) {
      const head = document.createElement('div')
      head.className = 'file'
      const path = fig.dataset.file
      head.innerHTML = `<span>${esc(path.split('/').slice(-3).join('/'))}</span><a href="${REPO}${esc(path)}" target="_blank" rel="noopener">full file ↗</a>`
      fig.prepend(head)
    }
  }
  document.querySelectorAll('figure.code').forEach(renderCode)

  /* ---------- copy buttons ---------- */
  for (const b of document.querySelectorAll('[data-copy]')) {
    b.addEventListener('click', async () => {
      const text = b.closest('.cmd').querySelector('pre').textContent
      try { await navigator.clipboard.writeText(text); b.textContent = 'Copied ✓' } catch { b.textContent = 'Select it ↖' }
      setTimeout(() => { b.textContent = 'Copy' }, 1800)
    })
  }

  /* ---------- checklists: <ul class="todo" data-store="key"> ---------- */
  for (const ul of document.querySelectorAll('ul.todo[data-store]')) {
    const key = `cdnd-guide-${ul.dataset.store}`
    const done = store.get(key, [])
    ul.querySelectorAll('li').forEach((li, i) => {
      const box = li.querySelector('input[type=checkbox]')
      if (!box) return
      box.checked = done.includes(i)
      li.classList.toggle('done', box.checked)
      box.addEventListener('change', () => {
        li.classList.toggle('done', box.checked)
        const now = [...ul.querySelectorAll('li')].flatMap((x, j) => (x.querySelector('input')?.checked ? [j] : []))
        store.set(key, now)
      })
    })
  }

  /* ---------- quizzes: <div class="q" data-answer="index"> ---------- */
  for (const q of document.querySelectorAll('.q[data-answer]')) {
    const right = Number(q.dataset.answer)
    const opts = [...q.querySelectorAll('.opts button')]
    opts.forEach((b, i) => b.addEventListener('click', () => {
      opts.forEach((o, j) => { o.classList.remove('right', 'wrong'); if (j === right) o.classList.add('right') })
      if (i !== right) b.classList.add('wrong')
      const ex = q.querySelector('.explain')
      if (ex) { ex.hidden = false; ex.firstElementChild?.replaceChildren(i === right ? 'Right. ' : 'Not quite. ') }
    }))
  }

  /* ---------- reading progress: [data-mark-read="ch0"] buttons, [data-read-badge="ch0"] on the contents page ---------- */
  const read = new Set(store.get('cdnd-guide-read', []))
  const paint = () => {
    for (const el of document.querySelectorAll('[data-read-badge]')) el.hidden = !read.has(el.dataset.readBadge)
    for (const b of document.querySelectorAll('[data-mark-read]')) b.textContent = read.has(b.dataset.markRead) ? 'Read ✓ (click to undo)' : 'Mark this chapter read'
    const count = document.querySelector('[data-read-count]')
    if (count) count.textContent = String(read.size)
  }
  for (const b of document.querySelectorAll('[data-mark-read]')) {
    b.addEventListener('click', () => { const id = b.dataset.markRead; read.has(id) ? read.delete(id) : read.add(id); store.set('cdnd-guide-read', [...read]); paint() })
  }
  paint()

  window.Guide = { esc, store, renderCode, bindGlossary }
}
