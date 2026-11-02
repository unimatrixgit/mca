import './pages.mjs';
import {pageArea,staffAreasFor} from './page-groups.mjs';

const dashboard=document.getElementById('dashboard');
const embedded=new URLSearchParams(location.search).get('embedded')==='1';
const accountPortals={
 marketplace:{label:'Marketplace Portal',home:'marketplace-home',pages:['marketplace-home','shop-settings','exchange-settings','planetary-inventory','kits-exchange','transactions','rewards-codes','kit-checkout'],description:'Trade, design and explore the imperial marketplace.'},
 adventure:{label:'Adventure Portal',home:'adventure-home',pages:['adventure-home','quests','discovery-rewards','clan-quests','clans','clan-portal','clan-shops','clan-kits','clan-bank','clan-settings','clan-quests-player','clan-quests-clan','clan-quests-guild','imperial-atelier'],description:'Your quests, discoveries and place in the realms.'},
 account:{label:'Your Account Portal',home:'account-home',pages:['account-home','user-home','personal-bank','tickets','game','site-profile','security','git-control'],description:'Your profile, linked identity, personal bank and support.'}
};
const staffPortals=[['website-staff','Website','website-staff-home'],['community-staff','Community','staff-home'],['game-staff','Minecraft','game-staff-home'],['network-staff','Network','network-staff-home'],['webmail','Webmail','webmail']];
const portalFor=page=>Object.keys(accountPortals).find(key=>accountPortals[key].pages.includes(page))||'account';
let user=null,lastAccount='marketplace-home',lastStaff=null,queued=false;
const iconFor=page=>page==='bans'||page==='ban-evidence'?'seals/department-ban-appeals-150.png':page.includes('ticket')?'seals/department-support-150.png':page.includes('forum')?'seals/department-forum-support-150.png':page.includes('shop')?'nav-shop-v1.webp':page.includes('exchange')||page.includes('transaction')?'nav-exchange-v2.webp':page.includes('kit')||page.includes('atelier')?'clan-atelier-v1.webp':page.includes('server')||page.includes('network')?'seals/department-network-staff-150.png':page.includes('mail')?'nav-support-v2.webp':page.includes('git')?'nav-manual-v2.webp':page.includes('quest')||page.includes('clan')?'nav-clan-v2.webp':page.includes('rewards')?'seals/season-tavren-ii-150.png':page.includes('staff')||page.includes('permission')||page==='luckperms'||page==='items'?'nav-staff-v2.webp':page.includes('posts')?'nav-discord-v2.webp':'nav-account-v2.webp';
function decorate(target,file){let img=target.querySelector(':scope>img.cp-section-art');if(img)return;img=document.createElement('img');img.className='cp-section-art';img.src='https://www.dunerealms.com/assets/'+file;img.alt='';img.width=34;img.height=34;img.loading='lazy';target.prepend(img);}

if(dashboard&&!embedded){
 document.body.classList.add('cp-centers-ready');
 const centers=document.createElement('nav');centers.className='cp-center-nav';centers.setAttribute('aria-label','Account and staff centers');
 const portalNav=document.createElement('nav');portalNav.className='cp-portal-nav';portalNav.setAttribute('aria-label','Portal menu');
 const breadcrumb=document.createElement('p');breadcrumb.className='cp-center-breadcrumb';
 dashboard.before(centers,portalNav,breadcrumb);
 const makeButton=(text,action)=>{const b=document.createElement('button');b.type='button';b.textContent=text;b.onclick=action;return b;};
 const accountButton=makeButton('Account Center',()=>navigate(lastAccount,'account'));
 const staffButton=makeButton('Staff Center',()=>{const available=staffAreasFor(user);if(available.length)navigate(lastStaff||staffPortals.find(([area])=>available.includes(area))[2],'staff');});
 centers.append(accountButton,staffButton);
 for(const [key,definition]of Object.entries(accountPortals)){
  const section=document.createElement('section');section.id=definition.home;section.className='cp-portal-overview';
  const eyebrow=document.createElement('p');eyebrow.className='eyebrow';eyebrow.textContent='ACCOUNT CENTER';
  const h=document.createElement('h2');h.textContent=definition.label;
  const p=document.createElement('p');p.textContent=definition.description;
  const grid=document.createElement('div');grid.className='cp-portal-overview-grid';
  section.append(eyebrow,h,p,grid);dashboard.append(section);
 }
 const contextHome=document.createElement('a');contextHome.className='cp-portal-context-home';contextHome.textContent='Portal overview';contextHome.href='#marketplace-home';
 const accountMenu=dashboard.querySelector('nav[data-cp-section-menu="user"]');accountMenu?.append(contextHome);

 function navigate(page,center,area){
  const url=new URL(location.href);url.hash=page;url.searchParams.set('center',center);
  if(area?.endsWith('-staff'))url.searchParams.set('staff-area',area);else url.searchParams.delete('staff-area');
  history.pushState(null,'',url);window.dispatchEvent(new PopStateEvent('popstate'));
 }
 function paint(){
  centers.hidden=portalNav.hidden=breadcrumb.hidden=!user;if(!user){document.body.removeAttribute('data-cp-center');return;}
  const active=dashboard.querySelector(':scope>section.cp-page-active')?.id||location.hash.slice(1)||'user-home';
  const staffAreas=staffAreasFor(user),baseArea=pageArea(active),isStaff=baseArea.endsWith('-staff')||baseArea==='webmail';
  const center=isStaff?'staff':'account';document.body.dataset.cpCenter=center;staffButton.hidden=!staffAreas.length;
  accountButton.setAttribute('aria-pressed',String(center==='account'));staffButton.setAttribute('aria-pressed',String(center==='staff'));
  let label;
  if(center==='account'){
   lastAccount=active;const key=portalFor(active),definition=accountPortals[key];label=definition.label;
   const signature='account:'+key;
   if(portalNav.dataset.signature!==signature){portalNav.dataset.signature=signature;portalNav.replaceChildren();for(const [id,p]of Object.entries(accountPortals)){const b=makeButton(p.label,()=>navigate(p.home,'account'));if(id===key)b.setAttribute('aria-current','page');portalNav.append(b);}}
   if(accountMenu){
    accountMenu.dataset.currentPortal=key;
    for(const a of accountMenu.querySelectorAll(':scope>a[href^="#"]')){
     if(a===contextHome)continue;const group=portalFor(a.hash.slice(1));if(a.dataset.portalGroup!==group)a.dataset.portalGroup=group;
    }
    const href='#'+definition.home;if(contextHome.getAttribute('href')!==href)contextHome.setAttribute('href',href);
    if(contextHome.dataset.portalGroup!==key)contextHome.dataset.portalGroup=key;
    if(active===definition.home)contextHome.setAttribute('aria-current','page');else contextHome.removeAttribute('aria-current');
   }
  }else{
   lastStaff=active;const requested=new URLSearchParams(location.search).get('staff-area');const area=requested&&staffAreas.includes(requested)?requested:baseArea;
   const choices=staffPortals.filter(([id])=>id==='webmail'?staffAreas.length&&user.permissions.includes('web.menu.security'):staffAreas.includes(id));
   label=choices.find(([id])=>id===area)?.[1]||'Staff';const signature='staff:'+area+':'+choices.map(c=>c[0]).join(',');
   if(portalNav.dataset.signature!==signature){portalNav.dataset.signature=signature;portalNav.replaceChildren();for(const [id,name,home]of choices){const b=makeButton(name,()=>navigate(home,'staff',id));if(id===area)b.setAttribute('aria-current','page');portalNav.append(b);}}
  }
  const pageName=dashboard.querySelector(':scope>section.cp-page-active>h2')?.textContent||'Overview';
  const trail=(center==='staff'?'Staff Center':'Account Center')+' / '+label+' / '+pageName;if(breadcrumb.textContent!==trail)breadcrumb.textContent=trail;
  decorate(accountButton,'nav-account-v2.webp');decorate(staffButton,'nav-staff-v2.webp');
  for(const b of portalNav.querySelectorAll('button')){const text=b.textContent;decorate(b,text.includes('Marketplace')?'nav-shop-v1.webp':text.includes('Adventure')?'nav-clan-v2.webp':text==='Community'?'seals/department-ban-appeals-150.png':text==='Network'?'seals/department-network-staff-150.png':text==='Website'?'nav-staff-v2.webp':text==='Minecraft'?'nav-clan-v2.webp':text==='Webmail'?'nav-support-v2.webp':'nav-account-v2.webp');}
  for(const a of dashboard.querySelectorAll('nav[data-cp-section-menu]>a[href^="#"],.staff-shortcut-buttons>a[href^="#"]'))decorate(a,iconFor(a.hash.slice(1)));
  for(const [key,p]of Object.entries(accountPortals)){
   const grid=document.querySelector('#'+p.home+' .cp-portal-overview-grid');
   const links=[...accountMenu?.querySelectorAll(':scope>a[href^="#"]')||[]].filter(a=>a!==contextHome&&!a.hidden&&p.pages.includes(a.hash.slice(1))&&!['user-home',p.home].includes(a.hash.slice(1)));
   const signature=links.map(a=>a.hash+':'+a.textContent).join('|');if(grid.dataset.signature===signature)continue;grid.dataset.signature=signature;grid.replaceChildren();
   for(const a of links){const card=document.createElement('a');card.className='cp-portal-tile';card.href=a.hash;const image=document.createElement('img');const page=a.hash.slice(1);image.src='https://www.dunerealms.com/assets/'+(page.includes('kit')||page.includes('atelier')?'clan-atelier-v1.webp':page.includes('shop')||page.includes('rewards')?'nav-shop-v1.webp':key==='adventure'?'nav-clan-v2.webp':key==='account'?'nav-account-v2.webp':'nav-exchange-v2.webp');image.alt='';image.width=52;image.height=52;const title=document.createElement('strong');title.textContent=a.textContent.trim();const open=document.createElement('span');open.textContent='Open →';card.append(image,title,open);grid.append(card);}
  }
 }
 function schedule(){if(queued)return;queued=true;queueMicrotask(()=>{queued=false;paint();});}
 window.addEventListener('dune:page',schedule);window.addEventListener('hashchange',schedule);window.addEventListener('popstate',schedule);
 window.addEventListener('dune:session',event=>{user=event.detail;portalNav.removeAttribute('data-signature');schedule();});
 new MutationObserver(schedule).observe(dashboard,{childList:true,subtree:true});
 const response=await fetch('/api/auth/session',{cache:'no-store'});if(response.ok){user=(await response.json()).user;const url=new URL(location.href);if(!url.hash){const areas=staffAreasFor(user);if(url.searchParams.get('center')==='staff'&&areas.length)navigate(staffPortals.find(([id])=>areas.includes(id))[2],'staff',areas[0]);else if(user)navigate('marketplace-home','account');}paint();}
}
