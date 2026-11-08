(()=>{
 const cp=location.hostname==='cp.dunerealms.com';
 if(!cp&&!['/','/index.html'].includes(location.pathname))return;
 let user=null,revision=0;
 const site='https://www.dunerealms.com';
 function render(){
  const panel=document.querySelector('.dune-page-marker'),worlds=panel?.querySelector('.world-status');
  if(!panel||!worlds)return;
  const side=cp?document.querySelector('.cp-panel-frame:not([hidden])>.cp-panel-sidebar'):null;
  const sideReady=side&&['user','staff'].includes(side.parentElement.dataset.panel);
  const old=document.querySelector('.portal-account-card');
  if(cp&&!sideReady){old?.remove();return;}
  if(old?.dataset.identity===(user||''))return;
  old?.remove();panel.querySelector('.portal-welcome-card')?.remove();
  if(cp&&!user){panel.classList.remove('portal-account-welcome');return;}
  if(!cp)panel.classList.add('portal-account-welcome');
  const box=document.createElement('aside');box.className='portal-account-card';box.dataset.identity=user||'';box.setAttribute('aria-label',user?'Your Imperial Postbox':'Your DuneRealms account');
  const art=document.createElement('img');art.src=site+(user?'/assets/imperial-postbox-v1.png':'/assets/nav-account-full-v2.webp');art.alt=user?'Emerald and gold Minecraft imperial postbox with sealed letters':'';art.width=user?200:80;art.height=user?300:80;if(user)art.className='imperial-postbox-art';
  const heading=document.createElement('h2');heading.textContent=user?'Imperial Postbox':'Join the caravan';
  const text=document.createElement('p');text.textContent=user?'Your private correspondence.':'Create your account and begin your DuneRealms journey.';
  const actions=document.createElement('div');
  const links=user?[['Open Internal Mail','https://cp.dunerealms.com/#shared-inbox']]:[['Register','https://cp.dunerealms.com/?mode=register'],['Login','https://cp.dunerealms.com/']];
  for(const[label,url]of links){const a=document.createElement('a');a.textContent=label;a.href=url;actions.append(a);}
  box.append(art,heading,text,actions);
  if(user&&document.querySelector('.dune-release-badge')?.textContent.match(/\d+(?:\.\d+){3}/)?.[0]==='1.0.0.4'){const ribbon=document.createElement('span');ribbon.className='postbox-new-ribbon';ribbon.textContent='NEW';ribbon.setAttribute('aria-label','New this release');box.append(ribbon);}
  if(user){const status=document.createElement('p');status.className='postbox-status';status.textContent='Mail notifications are being connected.';box.append(status);}
  if(user&&!cp){
   const welcome=document.createElement('aside');welcome.className='portal-welcome-card';welcome.setAttribute('aria-label','Your DuneRealms account');
   const portrait=document.createElement('img');portrait.src=site+'/assets/nav-account-full-v2.webp';portrait.alt='';portrait.width=portrait.height=80;
   const title=document.createElement('h2');title.textContent='Welcome, '+user;
   const copy=document.createElement('p');copy.textContent='Your place in the imperial ledger.';
   const account=document.createElement('a');account.href='https://cp.dunerealms.com/';account.textContent='Account centre';
   welcome.append(portrait,title,copy,account);panel.insertBefore(welcome,worlds);
  }
  if(cp){box.classList.add('cp-sidebar-postbox');side.insertBefore(box,side.querySelector('.cp-panel-category'));panel.classList.remove('portal-account-welcome');panel.querySelector('.portal-welcome-card')?.remove();}else panel.insertBefore(box,worlds);
 }
 new MutationObserver(render).observe(document.body,{childList:true,subtree:true});
 async function refresh(){const current=++revision;try{const r=await fetch('https://cp.dunerealms.com/api/welcome',{credentials:'include',cache:'no-store'});const d=r.ok?await r.json():null;if(current===revision){user=typeof d?.username==='string'?d.username:null;render();}}catch{if(current===revision){user=null;render();}}}
 window.addEventListener('dune:session',event=>{revision++;user=event.detail?.username||null;render();});
 window.addEventListener('pageshow',refresh);window.addEventListener('focus',refresh);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();});
 render();refresh();
})();
