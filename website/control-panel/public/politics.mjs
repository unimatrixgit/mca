const el=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;};
const asset=name=>'https://www.dunerealms.com/assets/'+name;
const specs={
 landsraad:['THE GREAT ASSEMBLY','Landsraad','Your voice. Your House. The future of the Imperium.','seals/office-landsraad.png'],
 'house-elections':['YOUR HOUSE, YOUR VOICE','House Elections','A chamber reserved for the faction you chose in game.','seals/office-house_leader.png'],
 'imperial-elections':['THE IMPERIAL BALLOT','Imperial Elections','The future seat of the Imperial election.','seals/office-emperor.png'],
 'imperial-command':['BY IMPERIAL DECREE','Imperial Command','The Emperor’s future workspace for water and spice rights.','seals/office-emperor.png'],
 'imperial-water':['THE WATER CHARTER','Water Rights','Stewardship of the Imperium’s most precious resource.','items/office_water.png'],
 'imperial-spice':['THE SPICE CHARTER','Spice Rights','The Emperor’s future register of spice rights.','items/office_spice.png']
};
const dashboard=document.getElementById('dashboard'),nav=dashboard?.querySelector('nav'),views={};let user=null,serial=0,context=null;
const electionLinks=[['Landsraad','landsraad','seals/office-landsraad.png'],['House Elections','house-elections','seals/office-house_leader.png'],['Imperial Elections','imperial-elections','seals/office-emperor.png']];
const commandLinks=[['Imperial Command','imperial-command','seals/office-emperor.png'],['Water','imperial-water','items/office_water.png'],['Spice','imperial-spice','items/office_spice.png']];
function artLink(label,page,art){const a=el('a');a.href='#'+page;const img=el('img');img.src=asset(art);img.alt='';img.width=img.height=32;a.append(img,el('span',label));return a;}
function card(title,copy,art){const box=el('article',undefined,'politics-card');if(art){const img=el('img');img.src=asset(art);img.alt='';img.width=80;img.height=80;box.append(img);}box.append(el('h3',title),el('p',copy));return box;}
function disabled(text){const b=el('button',text);b.type='button';b.disabled=true;return b;}
function demoNote(){return el('p','DEMO DATA · Fictional names, votes and support. Nothing is submitted or saved.','politics-demo-note');}
function demoSupport(counter=false){const box=el('div',undefined,'politics-demo-support');box.append(demoNote());for(const [name,state]of [['House Amber','Supports'],['House Jade',counter?'Supports':'Awaiting agreement']]){const row=el('p');row.append(el('strong',name+' · '),el('span',state));box.append(row);}box.append(el('strong',counter?'Demo outcome: 2 / 2 Houses — election denied':'Demo progress: 1 / 2 Houses'));return box;}
function electionPreview(imperial=false){
 const host=el('div',undefined,'politics-content');
 const application=card('Submit your application for the upcoming election',imperial?'Everyone can submit an application to become Emperor.':'Members of your faction can apply to lead their own faction.','seals/'+(imperial?'office-emperor.png':'office-house_leader.png'));
 application.append(demoNote());const fields=el('div',undefined,'politics-charter');const label=el('label','Your motivation and plans'),motivation=el('textarea');motivation.rows=5;motivation.value=imperial?'DEMO: I would bring the Houses together, publish clear decisions and protect fair access to water and spice.':'DEMO: I would listen to our faction, organise community projects and represent our members in the Landsraad.';label.append(motivation);
 const accept=el('label',undefined,'politics-accept'),check=el('input');check.type='checkbox';accept.append(check,el('span','Yes, I accept the responsibility of standing for election.'));fields.append(label,accept,disabled('Submit application — preview only'));application.append(fields);host.append(application);
 const ballot=card('Vote',imperial?'House leaders have 5 votes: place them on one candidate or divide them across several. Try the demo allocation below.':'All faction members can vote for a leader of their own faction. Choose one demo candidate.');ballot.append(demoNote(),el('p','Sorted by current votes · highest first. Demo allocations do not change these example totals.'));
 const names=['DuneWeaver','AmberWarden','SandCartographer','JadeVoyager','SpiceScribe','CanyonKeeper','SietchGardener','MoonSurveyor','DesertMason','WaterArchivist','GoldenNomad','DawnBuilder'];
 const candidates=names.map((name,i)=>({name:'Demo '+name,votes:84-i*5,id:i,motivation:'I want to represent our community through open discussion, reliable leadership and shared projects. This is a fictional candidate statement for layout review.'}));
 let page=0,selected=null;const allocations=new Map();const total=()=>[...allocations.values()].reduce((a,b)=>a+b,0);const status=el('p',undefined,'politics-ballot-status');status.setAttribute('role','status');const grid=el('div',undefined,'politics-candidates'),pages=el('nav',undefined,'politics-pagination');pages.setAttribute('aria-label',(imperial?'Imperial':'House')+' demo candidate pages');
 const updateStatus=()=>{status.textContent=imperial?total()+' of 5 demo votes allocated':selected===null?'Choose one demo candidate':'Selected: '+candidates[selected].name;};
 function draw(){grid.replaceChildren();pages.replaceChildren();updateStatus();for(const c of candidates.slice(page*9,page*9+9)){
 const tile=el('article',undefined,'politics-candidate');const head=el('div',undefined,'politics-demo-head');head.setAttribute('aria-hidden','true');for(let i=0;i<64;i++)head.append(el('span',undefined,[0,1,2,3,4,5,6,7,8,15].includes(i)?'hair':[26,29].includes(i)?'eye':[43,44].includes(i)?'mouth':'skin'));tile.append(head,el('h4',c.name),el('p',c.votes+' demo votes'));
 if(imperial){const controls=el('div',undefined,'politics-allocation'),minus=el('button','−'),plus=el('button','+'),count=el('output',String(allocations.get(c.id)||0));minus.type=plus.type='button';minus.setAttribute('aria-label','Remove demo vote from '+c.name);plus.setAttribute('aria-label','Add demo vote to '+c.name);count.setAttribute('aria-label','Allocated votes for '+c.name);minus.disabled=!allocations.get(c.id);plus.disabled=total()>=5;
 minus.addEventListener('click',()=>{allocations.set(c.id,Math.max(0,(allocations.get(c.id)||0)-1));draw();});plus.addEventListener('click',()=>{if(total()<5)allocations.set(c.id,(allocations.get(c.id)||0)+1);draw();});controls.append(minus,count,plus);tile.append(controls);
 }else{const choice=el('label',undefined,'politics-accept'),radio=el('input');radio.type='radio';radio.name='house-demo-vote';radio.value=String(c.id);radio.checked=selected===c.id;radio.addEventListener('change',()=>{selected=c.id;updateStatus();});choice.append(radio,el('span','Vote for '+c.name));tile.append(choice);}
 const details=el('details'),summary=el('summary','Motivation');details.append(summary,el('p',c.motivation));tile.append(details);grid.append(tile);}
 const prev=el('button','Previous'),next=el('button','Next');prev.type=next.type='button';prev.disabled=page===0;next.disabled=(page+1)*9>=candidates.length;prev.addEventListener('click',()=>{page--;draw();});next.addEventListener('click',()=>{page++;draw();});pages.append(prev,el('span','Page '+(page+1)+' of '+Math.ceil(candidates.length/9)),next);
 }draw();ballot.append(status,grid,pages,disabled('Submit vote — preview only'));host.append(ballot);return host;
}
function rallyPreview(){
 const box=card('Force elections','Two different Houses must agree before a new election is held.','seals/office-landsraad.png');
 const start=el('section');start.append(el('h4','Start a rally'),el('p','Call for a new election and explain why the chamber should return to the ballot.'));
 const label=el('label','Reason for the rally'),reason=el('textarea');reason.rows=4;reason.placeholder='Explain why a new election is needed';reason.disabled=true;label.append(reason);start.append(label,disabled('Start a rally'));
 const support=el('section');support.append(el('h4','House agreement'),el('p','Required support: 2 distinct Houses. Multiple endorsements from the same House count only once.'),el('p','No active rally — this is a design preview.'),disabled('Support this rally'));
 reason.value='DEMO: We ask the Houses to support a fresh election and a renewed mandate.';support.append(demoSupport());const grid=el('div',undefined,'politics-grid');start.className='politics-charter';support.className='politics-card';grid.append(start,support);box.append(grid);return box;
}
function counterRallyPreview(){
 const box=card('Counter rally','Rally House support against the proposed election. Agreement from two distinct Houses denies the election.','seals/office-emperor.png');
 const label=el('label','Reason for the counter rally'),reason=el('textarea');reason.rows=4;reason.placeholder='Explain why the proposed election should not proceed';reason.disabled=true;label.append(reason);
 const draft=el('section',undefined,'politics-charter');draft.append(label,disabled('Start a counter rally'));
 reason.value='DEMO: Maintain the current mandate and allow the agreed programme to continue.';box.append(draft,el('p','Required support: 2 distinct Houses. Multiple endorsements from the same House count only once.'),el('p','No active counter rally — design preview only.'),demoSupport(true),disabled('Support this counter rally'));return box;
}
function revoltPreview(){
 const box=card('Start revolt','Bring a call for revolt before the Landsraad.','seals/office-landsraad.png');
 box.append(el('p','Design placeholder. Support requirements and consequences have not been defined; this action is not active.'),disabled('Start revolt'));return box;
}
for(const[id,[eyebrow,title,description,seal]]of Object.entries(specs)){
 const section=el('section',undefined,'politics-page');section.id=id;section.hidden=true;
 const hero=el('header',undefined,'cp-user-page-hero politics-hero');const image=el('img');image.src=asset(seal);image.alt='';image.width=112;image.height=112;const copy=el('div');copy.append(el('small',eyebrow),el('h2',title),el('p',description));hero.append(image,copy);
 const tabs=el('nav',undefined,'cp-context-links');tabs.setAttribute('aria-label',title+' pages');for(const[label,page,art]of(id.startsWith('imperial-')&&id!=='imperial-elections'?commandLinks:electionLinks)){const a=artLink(label,page,art);if(page===id)a.setAttribute('aria-current','page');tabs.append(a);}
 const preview=el('aside',undefined,'politics-preview-note');preview.append(el('strong','WEBSITE DESIGN PREVIEW'),el('span','Voting and rights grants are not active. These pages do not change the game.'));
 const content=el('div',undefined,'politics-content');section.append(hero,tabs,preview,content);dashboard?.append(section);nav?.append(artLink(title,id,seal));views[id]={section,content};
}
function render(){
 for(const[id,v]of Object.entries(views)){v.section.hidden=!user?.permissions?.includes('web.menu.game');v.content.replaceChildren();}
 const landing=views.landsraad.content,grid=el('div',undefined,'politics-grid');
 for(const[title,page,art,copy]of [['House Elections','house-elections','seals/office-house_leader.png','Enter your own House chamber. Only your verified in-game faction will appear.'],['Imperial Elections','imperial-elections','seals/office-emperor.png','Follow the future Imperial ballot, candidates and results.']]){const box=card(title,copy,art);box.append(artLink('Open chamber',page,art));grid.append(box);}landing.append(grid,card('From nomination to result','The planned flow is nomination, voting, then a recorded result. Eligibility, terms and ballot rules will be defined before voting opens.'));
 const house=views['house-elections'].content;
 if(context?.faction){const name=context.faction[0].toUpperCase()+context.faction.slice(1);house.append(card(name+' · House chamber','Your faction was confirmed from your linked Minecraft account. The candidates below are fictional examples, not members of your House.','seals/office-house_leader.png'),electionPreview());}
 else{const title=context?.linked===false?'Link your Minecraft account':context?'House not confirmed':'Checking your linked House';const box=card(title,context?.linked===false?'Link your Minecraft identity to enter the chamber belonging to the faction you chose in game.':'Your House has not been confirmed. Other House elections remain hidden.','seals/office-house_leader.png');box.append(artLink('Minecraft account','game','nav-clan-v2.webp'));house.append(box);}
 landing.append(rallyPreview(),revoltPreview());
 const imperial=views['imperial-elections'].content;imperial.append(electionPreview(true));
 const command=views['imperial-command'].content;command.append(card(context?.emperor?'Imperial office confirmed':'Reserved for the Emperor',context?.emperor?'Your linked character currently holds the Imperial office. This remains a read-only design preview.':'Only the current Emperor will be able to grant water and spice rights. Viewing this preview does not grant that authority.','seals/office-emperor.png'));const rights=el('div',undefined,'politics-grid');for(const[label,page,art,copy]of [['Water','imperial-water','items/office_water.png','Design the future register of water-rights grants.'],['Spice','imperial-spice','items/office_spice.png','Design the future register of spice-rights grants.']]){const box=card(label+' Rights',copy,art);box.append(artLink('Open '+label.toLowerCase()+' chamber',page,art));rights.append(box);}command.append(rights);
 for(const[id,resource,art]of [['imperial-water','water','items/office_water.png'],['imperial-spice','spice','items/office_spice.png']]){const host=views[id].content;const draft=el('section',undefined,'politics-charter');draft.append(el('small','IMPERIAL CHARTER · DESIGN ONLY'),el('h3','Grant '+resource+' rights'),el('p','A future decree will identify its recipient, scope and duration, with a reason and confirmation by the current Emperor.'));for(const[label,type,placeholder]of [['Recipient','select','Eligible recipient — not connected'],['Scope','input','Rights and territory — to be defined'],['Duration','input','Term — to be defined'],['Imperial decree','textarea','Reason for this grant']]){const wrap=el('label',label),field=el(type);field.disabled=true;if(type==='select')field.append(el('option',placeholder));else field.placeholder=placeholder;wrap.append(field);draft.append(wrap);}draft.append(disabled('Grant '+resource+' rights — not active'));host.append(draft,card('Rights register','No website grants have been recorded. The future register will distinguish active, expired and revoked charters.',art));}
 command.append(counterRallyPreview());
}
async function load(){if(!user?.permissions?.includes('web.menu.game'))return;const n=++serial;try{const r=await fetch('/api/politics/preview',{cache:'no-store'}),d=await r.json();if(n!==serial)return;context=r.ok?d:{linked:true,faction:null,emperor:false};render();}catch{if(n===serial){context={linked:true,faction:null,emperor:false};render();}}}
function session(next){user=next;serial++;context=null;render();if(views[location.hash.slice(1)])load();}
window.addEventListener('dune:session',e=>session(e.detail));window.addEventListener('dune:page',e=>{if(views[e.detail?.page])load();});
fetch('/api/auth/session',{cache:'no-store'}).then(r=>r.ok?r.json():null).then(d=>{if(d)session(d.user);}).catch(()=>{});
