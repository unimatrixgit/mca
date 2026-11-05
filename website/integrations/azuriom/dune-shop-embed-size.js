(()=>{
 if(window.parent===window)return;
 let parentOrigin=null,queued=false,last=0;
 function report(){queued=false;if(!parentOrigin||!document.body)return;
  const height=Math.ceil(document.body.getBoundingClientRect().height);
  if(height>=100&&height<=30000&&height!==last){last=height;window.parent.postMessage({type:'dune-shop-size',height},parentOrigin);}
 }
 function schedule(){if(!queued){queued=true;requestAnimationFrame(report);}}
 window.addEventListener('message',event=>{
  if(!['https://www.dunerealms.com','https://dunerealms.com','https://cp.dunerealms.com'].includes(event.origin)||event.source!==window.parent||event.data?.type!=='dune-shop-measure')return;
  parentOrigin=event.origin;document.documentElement.classList.add('dune-shop-embedded');last=0;schedule();
 });
 const style=document.createElement('style');style.textContent='html.dune-shop-embedded,html.dune-shop-embedded body,html.dune-shop-embedded #app{height:auto!important;min-height:0!important}html.dune-shop-embedded .min-vh-100{min-height:0!important}html.dune-shop-embedded .wrapper,html.dune-shop-embedded .main{height:auto!important;min-height:0!important}html.dune-shop-embedded{overflow-y:hidden!important}html.dune-shop-embedded #shop>.col-lg-3 .card{height:auto!important}';document.head.append(style);
 if(document.body)new ResizeObserver(schedule).observe(document.body);
 window.addEventListener('resize',schedule);document.addEventListener('load',schedule,true);
 document.fonts?.ready.then(schedule);
})();
