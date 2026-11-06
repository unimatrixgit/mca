/* Supplement the shared action system with native osTicket action semantics.
   Presentation only: never clicks, submits, changes form values or authorises an action. */
(() => {
 if (!document.body.classList.contains('dune-support-workspace')) return;
 const selector='a.ticket-action,a.confirm-action,#confirm-form input[type=submit],#confirm-form button[type=submit]';
 function kind(element) {
  if (element.closest('#confirm-form')) {
   return element.closest('form')?.querySelector('input[name=do]')?.value === 'delete' ? 'danger' : 'admin';
  }
  const href=element.getAttribute('href')||'';
  if (element.closest('li.danger') || /\/status\/delete(?:$|[?#])/.test(href) || href==='#banemail') return 'danger';
  if (href==='#unbanemail') return 'approve';
  return 'admin';
 }
 function refresh() {
  for (const element of document.querySelectorAll(selector)) {
   if (!element.classList.contains('dune-button')) element.classList.add('dune-button');
   const action=kind(element);
   if (element.dataset.duneAction!==action) element.dataset.duneAction=action;
   // Explicit native semantics take priority over the generic text classifier.
   if (element.dataset.duneAutoAction) delete element.dataset.duneAutoAction;
  }
 }
 refresh();
 let scheduled=false;
 new MutationObserver(() => {
  if (scheduled) return;
  scheduled=true;queueMicrotask(() => {scheduled=false;refresh();});
 }).observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['style','hidden','value']});
})();
