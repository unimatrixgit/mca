/* Progressive enhancement of osTicket's own authorised topic list and form.
 * Native select, validation, CSRF and dynamic-form change handlers remain intact. */
function enhanceTopic(select){
 if(select.dataset.duneTopicMenu)return;
 const choices=Array.from(select.options).filter(o=>o.value&&!o.disabled);if(!choices.length)return;
 select.dataset.duneTopicMenu='true';const form=select.form;if(!form)return;
 form.classList.add('dune-ticket-compose');
 const header=document.createElement('div');header.className='dune-topic-header';
 const eyebrow=document.createElement('p');eyebrow.className='dune-compose-eyebrow';eyebrow.textContent='DUNEREALMS / SUPPORT DESK';
 const title=document.createElement('h2');title.textContent='How can we help?';
 const help=document.createElement('p');help.textContent='Choose a topic, then tell us what happened. Your ticket stays in the appropriate support queue.';
 const menu=document.createElement('nav');menu.className='dune-topic-menu';menu.setAttribute('aria-label','Ticket topic');
 const buttons=[];
 for(const option of choices){const button=document.createElement('button');button.type='button';button.className='dune-topic-choice';button.textContent=option.textContent;button.dataset.topic=option.value;button.setAttribute('aria-pressed',String(select.value===option.value));button.onclick=()=>{if(select.value===option.value)return;select.value=option.value;select.dispatchEvent(new Event('change',{bubbles:true}));sync();};menu.append(button);buttons.push(button);}
 function sync(){for(const button of buttons)button.setAttribute('aria-pressed',String(button.dataset.topic===select.value));}
 menu.addEventListener('keydown',event=>{if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;const index=buttons.indexOf(event.target);if(index<0)return;event.preventDefault();const next=event.key==='Home'?0:event.key==='End'?buttons.length-1:(index+(event.key==='ArrowRight'?1:-1)+buttons.length)%buttons.length;buttons[next].focus();});
 select.addEventListener('change',sync);form.addEventListener('reset',()=>queueMicrotask(sync));
 header.append(eyebrow,title,help,menu);form.prepend(header);select.hidden=true;select.setAttribute('aria-hidden','true');
 const row=select.closest('tr');if(row){const previous=row.previousElementSibling;if(previous&&/^Help Topic\s*:?$/i.test(previous.textContent.trim()))previous.hidden=true;const error=row.querySelector('.error');if(error&&error.textContent.replace(/[\s*]/g,'')){const notice=document.createElement('p');notice.className='error';notice.textContent=error.textContent;notice.setAttribute('role','alert');header.append(notice);}row.hidden=true;}
 // The menu replaces only topic selection; server-side errors remain alongside
 // the original select and no ticket submission is triggered by a topic click.
}
function enhanceStatus(select){
 if(select.dataset.duneStatusMenu)return;select.dataset.duneStatusMenu='true';
 const options=Array.from(select.options).filter(option=>option.value&&!option.disabled),menu=document.createElement('div');menu.className='dune-ticket-status-menu';menu.setAttribute('role','group');menu.setAttribute('aria-label','Ticket status');
 const label=option=>option.textContent.replace(/\s*\(current\)\s*/ig,'').trim(),order=['open','resolved','closed'];
 options.sort((a,b)=>{const x=order.indexOf(label(a).toLowerCase()),y=order.indexOf(label(b).toLowerCase());return(x<0?99:x)-(y<0?99:y);});
 const open=options.find(option=>label(option).toLowerCase()==='open');if(open)select.value=open.value;
 const buttons=[];for(const option of options){const button=document.createElement('button');button.type='button';button.className='dune-topic-choice';button.textContent=label(option);button.dataset.status=option.value;button.dataset.duneAction=label(option).toLowerCase()==='resolved'?'approve':label(option).toLowerCase()==='closed'?'neutral':'admin';button.onclick=()=>{select.value=option.value;select.dispatchEvent(new Event('change',{bubbles:true}));sync();};buttons.push(button);menu.append(button);}
 function sync(){for(const button of buttons)button.setAttribute('aria-pressed',String(button.dataset.status===select.value));}
 select.addEventListener('change',sync);select.form?.addEventListener('reset',()=>queueMicrotask(()=>{if(open)select.value=open.value;sync();}));select.after(menu);select.hidden=true;sync();
}
function enhanceForms(){for(const select of document.querySelectorAll('select[name="topicId"]'))enhanceTopic(select);for(const select of document.querySelectorAll('select[name="reply_status_id"],select[name="note_status_id"]'))enhanceStatus(select);}
enhanceForms();
let scheduled=false;new MutationObserver(()=>{if(scheduled)return;scheduled=true;queueMicrotask(()=>{scheduled=false;enhanceForms();});}).observe(document.body,{childList:true,subtree:true});
