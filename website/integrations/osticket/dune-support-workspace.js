/* Native staff navigation replaces #content through PJAX. Restore presentation only. */
(() => {
  if (document.body.classList.contains('dune-support-client')) {
    const table = document.querySelector('#ticketTable');
    if (table) {
      const seals = {support:'department-support-150.png',sales:'department-sales-150.png',legal:'department-legal-v2-150.png','ban appeals':'department-ban-appeals-150.png','forum support':'department-forum-support-150.png','dev team':'department-dev-150.png',development:'department-dev-150.png'};
      for (const row of table.querySelectorAll('tbody tr')) {
        if (row.cells.length !== 5) continue;
        const cell = row.cells[4], name = cell.textContent.trim().toLowerCase(), asset = seals[name];
        if (!asset || cell.querySelector('.dune-ticket-department-seal')) continue;
        const img = document.createElement('img');
        img.className = 'dune-ticket-department-seal';img.src = 'https://www.dunerealms.com/assets/seals/'+asset;
        img.alt = cell.textContent.trim()+' department seal';img.width = img.height = 72;img.loading = 'lazy';
        cell.append(img);row.classList.add('dune-ticket-has-seal');
      }
    }
  }
  if (!document.body.classList.contains('dune-support-staff')) return;
  const initial = document.querySelector('.dune-support-desk-heading');
  const root = document.querySelector('#container');
  if (!initial || !root) return;
  const template = initial.cloneNode(true);
  const refresh = () => {
    const content = document.querySelector('#content');
    if (!content || content.querySelector('.dune-support-desk-heading')) return;
    const hero = template.cloneNode(true);
    const ticket = location.pathname.endsWith('/tickets.php');
    hero.querySelector('h1').textContent = ticket ? 'Ticket desk' : 'Staff workspace';
    hero.querySelector('.dune-support-description').textContent = ticket
      ? 'Review assigned cases and respond through the original ticket controls below.'
      : 'Your casework, conversations and team tools in one place.';
    for (const link of hero.querySelectorAll('.dune-support-submenu a')) {
      if (ticket && link.getAttribute('href') === '/scp/tickets.php') link.setAttribute('aria-current','page');
      else link.removeAttribute('aria-current');
    }
    content.prepend(hero);
  };
  new MutationObserver(refresh).observe(root, {childList:true,subtree:true});
})();

