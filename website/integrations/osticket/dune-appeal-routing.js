const topic=document.getElementById('topicId');
topic?.addEventListener('change',()=>{if(topic.value==='2')location.assign('/appeal.php');},true);
const nav=document.getElementById('nav');
if(nav&&!nav.querySelector('[data-ban-appeal]')){const li=document.createElement('li'),link=document.createElement('a');link.dataset.banAppeal='';link.href='/appeal.php';link.textContent='Appeal a ban';li.append(link);nav.append(li);}
