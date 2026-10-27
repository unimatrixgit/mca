const status=document.querySelector('#link-status'),output=document.querySelector('#link-command');
async function call(method='GET',path='/api/game-link'){
 const response=await fetch(path,{method,headers:method==='POST'?{'Content-Type':'application/json'}:{},...(method==='POST'?{body:'{}'}:{})});
 const data=await response.json();if(!response.ok)throw Error(data.message||'Please try again later.');return data;
}
async function refresh(){try{const data=await call();status.textContent=data.linked?'Linked to '+data.link.minecraft_name+'.':'No verified game account linked.';document.querySelector('#unlink-game').hidden=!data.linked;}catch(error){status.textContent=error.message;}}
document.querySelector('#generate-link').addEventListener('click',async()=>{output.textContent='';try{const data=await call('POST');output.textContent=data.command;status.textContent='Enter this command yourself in game within 10 minutes. Do not share it. A new code replaces the previous one.';}catch(error){status.textContent=error.message;}});
document.querySelector('#unlink-game').addEventListener('click',async()=>{if(!confirm('Unlink your game account from this website account?'))return;try{await call('POST','/api/game-link/unlink');output.textContent='';await refresh();}catch(error){status.textContent=error.message;}});
document.querySelector('#refresh-link').addEventListener('click',refresh);refresh();
