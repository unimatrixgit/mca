const dashboard=document.getElementById('dashboard'),nav=dashboard?.querySelector('nav');
const pages={game:'cp.menu.game','planetary-inventory':'cp.menu.inventory',rewards:'cp.menu.rewards',security:'cp.menu.security','server-operations':'cp.menu.servers',staff:'cp.menu.administration',permissions:'cp.menu.permissions',items:'cp.menu.items','staff-manual':'cp.menu.staffmanual'};
let permissions=[],authenticated=false;
document.body.classList.add('cp-pages-ready');
function update(focus=false){
 if(!dashboard||!nav)return;
 const available=Object.keys(pages).filter(key=>permissions.includes(pages[key])&&document.getElementById(key));
 let active=location.hash.slice(1);if(!available.includes(active))active=available[0];
 for(const section of dashboard.querySelectorAll(':scope > section')){section.dataset.cpPage='';section.classList.toggle('cp-page-active',authenticated&&section.id===active);}
 for(const link of nav.querySelectorAll('a[href^="#"]')){const key=link.hash.slice(1),allowed=available.includes(key);link.hidden=!allowed;link.dataset.permission=pages[key]||'';if(key===active)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');}
 if(active){const section=document.getElementById(active);if(focus){const heading=section.querySelector('h2');if(heading){heading.tabIndex=-1;heading.focus({preventScroll:true});}}}
}
nav?.addEventListener('click',event=>{const link=event.target.closest('a[href^="#"]');if(!link||link.hidden)return;event.preventDefault();history.pushState(null,'',link.hash);update(true);});
window.addEventListener('hashchange',()=>update());window.addEventListener('popstate',()=>update());
window.addEventListener('dune:session',event=>{authenticated=!!event.detail;permissions=event.detail?.permissions||[];update();});
if(dashboard)new MutationObserver(()=>update()).observe(dashboard,{childList:true});
const response=await fetch('/api/auth/session',{cache:'no-store'});if(response.ok){const data=await response.json();authenticated=data.authenticated;permissions=data.user?.permissions||[];update();}
