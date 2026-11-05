/* Native frames report dimensions only to the trusted CP parent. No ticket data is sent. */
(()=>{
 if(window.parent===window)return;
 let parentOrigin=null,queued=false,last=0;
 function report(){queued=false;if(!parentOrigin||!document.body)return;const height=Math.ceil(Math.max(document.body.getBoundingClientRect().height,document.body.scrollHeight)+16);if(height>=100&&height<=200000&&height!==last){last=height;window.parent.postMessage({type:'dune-embed-size',height},parentOrigin);}}
 function schedule(){if(!queued){queued=true;requestAnimationFrame(report);}}
 window.addEventListener('message',event=>{if(event.origin!=='https://cp.dunerealms.com'||event.source!==window.parent||event.data?.type!=='dune-embed-measure')return;parentOrigin=event.origin;document.documentElement.classList.add('dune-cp-embedded');schedule();});
 const style=document.createElement('style');style.textContent='html.dune-cp-embedded,html.dune-cp-embedded body{height:auto!important;min-height:0!important}html.dune-cp-embedded{overflow-y:hidden!important}html.dune-cp-embedded body{overflow-y:visible!important}html.dune-cp-embedded .dev-submenu{display:none!important}';document.head.append(style);
 if(document.body)new ResizeObserver(schedule).observe(document.body);window.addEventListener('resize',schedule);document.addEventListener('load',schedule,true);document.fonts?.ready.then(schedule);
})();
