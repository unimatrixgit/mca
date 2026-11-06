// Actual installed Vite admin bundle and candidate modal fragment, isolated DOM; no real browser/record.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const [rootArg,candidate,qa]=process.argv.slice(2);if(!rootArg||!candidate||!qa)throw Error('Usage: admin-modal-test.cjs <native-root> <candidate-layout> <isolated-jsdom-prefix>');
const root=path.resolve(rootArg),{JSDOM,VirtualConsole}=require(path.resolve(qa,'node_modules/jsdom'));
const manifest=JSON.parse(fs.readFileSync(path.join(root,'public/build/manifest.json'),'utf8'));
const bundlePath=path.resolve(root,'public/build',manifest['resources/js/admin/admin.js'].file);
if(!bundlePath.startsWith(path.resolve(root,'public/build')+path.sep))throw Error('Bundle path outside native build');
const bundle=fs.readFileSync(bundlePath,'utf8'),layout=fs.readFileSync(candidate,'utf8');
const fragment=layout.match(/<!-- Delete confirm modal -->([\s\S]*?)<\/main>/)?.[1];if(!fragment)throw Error('Expected native modal context');
let checks=0;function same(a,b){checks++;if(a!==b)throw Error('Native admin modal assertion '+checks);}
function eventOnce(element,name){return new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Expected '+name)),1000);element.addEventListener(name,()=>{clearTimeout(timer);resolve();},{once:true});});}
(async()=>{
 const errors=[],vc=new VirtualConsole;vc.on('jsdomError',e=>errors.push(e.message));
 const dom=new JSDOM('<!doctype html><html><head><meta name="csrf-token" content="SYNTHETIC-QA-ONLY"></head><body>'+fragment+'</body></html>',{runScripts:'outside-only',url:'https://shop-qa.invalid/',pretendToBeVisual:true,virtualConsole:vc});
 try{
  const w=dom.window;let network=0,submitted=0;
  w.fetch=()=>{network++;throw Error('Network forbidden in modal fixture');};
  w.XMLHttpRequest.prototype.send=function(){network++;throw Error('XHR forbidden in modal fixture');};
  w.document.addEventListener('submit',e=>{submitted++;e.preventDefault();});
  w.HTMLFormElement.prototype.submit=function(){submitted++;throw Error('Form submission forbidden');};
  w.eval(bundle);
  const element=w.document.querySelector('#confirmDeleteModal'),modal=new w.bootstrap.Modal(element);
  const form=element.querySelector('#confirmDeleteForm');same(form.getAttribute('method'),'POST');same(fragment.includes("@method('DELETE')"),true);same(fragment.includes('@csrf'),true);
  for(const action of ['close','double-close','cancel','escape']){
   const shown=eventOnce(element,'shown.bs.modal');modal.show();await shown;
   same(element.classList.contains('show'),true);same(w.document.querySelectorAll('.modal-backdrop').length,1);
   const hidden=eventOnce(element,'hidden.bs.modal');
   if(action==='escape')element.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
   else if(action==='cancel')element.querySelector('.modal-footer button[data-bs-dismiss="modal"]').click();
   else{const close=element.querySelector('.modal-header .btn-close');close.click();if(action==='double-close')close.click();}
   await hidden;
   same(element.classList.contains('show'),false);same(w.document.body.classList.contains('modal-open'),false);same(w.document.querySelectorAll('.modal-backdrop').length,0);
   same(element.getAttribute('aria-hidden'),'true');same(submitted,0);same(network,0);same(errors.length,0);
  }
  console.log(JSON.stringify({passed:checks,actualInstalledAdminBundleSha256:crypto.createHash('sha256').update(bundle).digest('hex'),nativeCandidateModal:true,actions:['X','double X','Cancel','Escape','reopen'],networkCalls:network,formSubmissions:submitted,domErrors:errors.length,fullNativeBrowserAcceptance:false}));
 }finally{dom.window.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});
