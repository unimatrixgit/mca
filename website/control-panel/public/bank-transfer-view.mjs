const el=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;};
export function showOwnTransfer(container,bank,refresh){
 container.querySelector('.bank-transfer-form')?.remove();
 const form=el('form');form.className='bank-transfer-form bank-instructions';form.append(el('h3','Transfer between my accounts'),el('p','Move Dunemarks between your own current and savings accounts. No transfer fee.'));
 const field=(label,node)=>{const l=el('label',label);l.append(node);form.append(l);return node;};
 const source=field('From account',el('select'));for(const [key,label,number]of [['current','Current account',bank.currentAccountNumber],['savings','Savings account',bank.accountNumber]]){const o=el('option',label+' · '+number);o.value=key;source.append(o);}
 const destination=el('p');form.append(destination);const update=()=>{destination.textContent='To: '+(source.value==='current'?'Savings account · '+bank.accountNumber:'Current account · '+bank.currentAccountNumber);};source.onchange=update;update();
 const amount=field('Amount (DM)',el('input'));amount.type='number';amount.min='0.01';amount.max='1000000';amount.step='0.01';amount.required=true;
 const memo=field('Payment reference (optional)',el('input'));memo.maxLength=140;
 const review=el('button','Review transfer');review.type='submit';form.append(review);
 const details=el('section');details.className='imperial-account-card';details.hidden=true;form.append(details);
 const status=el('p');status.role='status';form.append(status);container.append(form);form.scrollIntoView({behavior:'smooth',block:'start'});
 form.onsubmit=e=>{e.preventDefault();if(!form.reportValidity())return;details.replaceChildren();details.hidden=false;
 const payload={source:source.value,amount:amount.value,memo:memo.value,requestId:crypto.randomUUID(),confirmed:true};
 details.append(el('h3','Confirm transfer'),el('p',source.options[source.selectedIndex].textContent),el('p',destination.textContent),el('p','Amount: '+amount.value+' DM · Fee: 0.00 DM'),el('p','Reference: '+(memo.value||'None')));
 const consent=el('input');consent.type='checkbox';const label=el('label',' I confirm these account details and amount.');label.prepend(consent);details.append(label);
 const send=el('button','Confirm transfer');send.type='button';send.disabled=true;consent.onchange=()=>send.disabled=!consent.checked;details.append(send);
 const edit=el('button','Edit');edit.type='button';details.append(edit);const inputs=[source,amount,memo,review];inputs.forEach(n=>n.disabled=true);edit.onclick=()=>{details.hidden=true;inputs.forEach(n=>n.disabled=false);status.textContent='';};
 send.onclick=async()=>{send.disabled=true;edit.disabled=true;consent.disabled=true;status.textContent='Submitting transfer…';try{const r=await fetch('/api/bank/transfer-own',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});const d=await r.json();if(!r.ok){status.textContent=d.message||'Confirmation unavailable. Retry this same request.';send.textContent='Retry same transfer';send.disabled=false;if(r.status===409||r.status===400||r.status===403)edit.disabled=false;return;}details.replaceChildren(el('h3','Transfer confirmed'),el('p',d.transfer.amountDunemarks+' DM transferred.'),el('code',d.transfer.reference));status.textContent='Both account balances and ledger entries were saved together.';const done=el('button','Refresh accounts');done.type='button';done.onclick=refresh;details.append(done);}catch{status.textContent='Confirmation unavailable. Retry the same transfer to avoid duplicates.';send.textContent='Retry same transfer';send.disabled=false;}};
 };
}
