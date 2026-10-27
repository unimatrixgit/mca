import './assets/altcha.js';

window.$altcha.algorithms.set('PBKDF2/SHA-256',()=>new Worker(new URL('./assets/altcha-pbkdf2.js',import.meta.url)));
document.addEventListener('click',event=>{
    const button=event.target.closest('.operator-contact .contact-reveal');
    if(!button||button.disabled)return;
    const container=button.closest('.operator-contact');
    for(const old of container.querySelectorAll('altcha-widget,.contact-status'))old.remove();
    button.disabled=true;
    const widget=document.createElement('altcha-widget');
    widget.setAttribute('challenge','/api/contact/challenge');
    widget.setAttribute('workers','2');
    const status=document.createElement('span');status.className='contact-status';status.setAttribute('role','status');status.textContent='Complete verification to show the contact email.';
    container.append(widget,status);
    widget.addEventListener('verified',async event=>{
      status.textContent='Checking verification…';
      try{
        const response=await fetch('/api/contact/reveal',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({payload:event.detail.payload})});
        const data=await response.json();if(!response.ok||typeof data.email!=='string')throw Error();
        const link=document.createElement('a');link.href='mailto:'+data.email;link.textContent=data.email;
        container.replaceChildren(link);link.focus();
      }catch{
        widget.remove();status.textContent='Verification could not complete. Please try again.';button.disabled=false;
      }
    });
    widget.addEventListener('statechange',event=>{
      if(event.detail.state==='error'||event.detail.state==='expired'){
        widget.remove();status.textContent='Verification unavailable or expired. Please try again.';button.disabled=false;
      }
    });
});
