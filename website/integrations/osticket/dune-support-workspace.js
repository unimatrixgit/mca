/* Native staff navigation replaces #content through PJAX. Restore presentation only. */
(() => {
  if (!document.body.classList.contains('dune-support-staff')) return;
  const initial = document.querySelector('.dune-support-workspace-hero');
  const root = document.querySelector('#container');
  if (!initial || !root) return;
  const template = initial.cloneNode(true);
  const refresh = () => {
    const content = document.querySelector('#content');
    if (!content || content.querySelector('.dune-support-workspace-hero')) return;
    const hero = template.cloneNode(true);
    const ticket = location.pathname.endsWith('/tickets.php');
    hero.querySelector('h1').textContent = ticket ? 'Ticket desk' : 'Staff workspace';
    hero.querySelector('div > p:last-child').textContent = ticket
      ? 'Review assigned cases and respond through the original ticket controls below.'
      : 'Your casework, conversations and team tools in one place.';
    content.prepend(hero);
  };
  new MutationObserver(refresh).observe(root, {childList:true,subtree:true});
})();
