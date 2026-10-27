const frame=document.querySelector('#account-panel');
window.addEventListener('message',event=>{if(event.origin!=='https://cp.dunerealms.com'||event.source!==frame.contentWindow||event.data?.type!=='dunerealms-account-height')return;const height=Number(event.data.height);if(Number.isFinite(height)&&height>=300&&height<=20000)frame.height=String(Math.ceil(height)+8);});
