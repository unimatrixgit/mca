import{readFileSync,writeFileSync,readdirSync}from'node:fs';
import{createHash}from'node:crypto';
import{fileURLToPath}from'node:url';
import path from'node:path';
const roots=['web','node/public','control-panel/public','integrations/osticket','integrations/azuriom'];
const dimensions=['required-empty','types-and-enums','unicode-boundaries','authorisation-identity','csrf-origin','uploads','replay-concurrency','keyboard-responsive'];
const mask=text=>text.replace(/<!--[^]*?-->|\/\*[^]*?\*\//g,m=>m.replace(/[^\r\n]/g,' ')).replace(/^\s*\/\/[^\r\n]*/gm,m=>m.replace(/[^\r\n]/g,' '));
const attribute=(raw,name)=>raw.match(new RegExp('(?:^|\\s)'+name+'\\s*=\\s*(["\u0027])([^]*?)\\1','i'))?.[2]||null;
function safeAction(raw){if(!raw)return'unspecified';try{const url=new URL(raw,'https://www.dunerealms.com');if(!['http:','https:'].includes(url.protocol)||url.username||url.password||!/^\/[a-z0-9_./-]*$/i.test(url.pathname))return'dynamic';return url.pathname||'/';}catch{return'dynamic';}}
export function qaTicketsFor(source){if(/\/dev-(?:tasks|view)\.php$/.test(source))return[344];if(source.startsWith('integrations/osticket/'))return[337];if(source.startsWith('integrations/azuriom/'))return /coupon|discount|promotion/i.test(source)?[343]:/admin|management|server/i.test(source)?[341]:[339];if(/ban|appeal|evidence|moderation/i.test(source))return[332];if(/clan/i.test(source))return[335];if(/news|post|content/i.test(source))return[336];if(source==='control-panel/public/panel.js'||source==='control-panel/public/index.html')return[333,340];return[334];}
export function discoverForms(source,text){const clean=mask(text),found=[],counts=new Map();
 const add=(kind,index,id,method,action)=>{const identity=kind+'|'+(id||'anonymous'),ordinal=(counts.get(identity)||0)+1;counts.set(identity,ordinal);const key=createHash('sha256').update(source+'|'+identity+'|'+ordinal).digest('hex').slice(0,16);found.push({key,source,line:clean.slice(0,index).split('\n').length,kind,identifier:id||null,occurrence:ordinal,method,action,qaTickets:qaTicketsFor(source),runtimeStatus:'unverified',validation:Object.fromEntries(dimensions.map(x=>[x,'not-run']))});};
 for(const m of clean.matchAll(/<form\b([^]*?)>/gi)){const a=m[1];add('literal-form',m.index,attribute(a,'id'),(attribute(a,'method')||'unspecified').toUpperCase(),safeAction(attribute(a,'action')));}
 for(const m of clean.matchAll(/\b(?:createElement|el)\s*\(\s*(['"])form\1\s*\)/g))add('dynamic-form-candidate',m.index,null,'UNSPECIFIED','dynamic');
 return found.sort((a,b)=>a.line-b.line||a.kind.localeCompare(b.kind)||a.key.localeCompare(b.key));
}
export function inventory(root=fileURLToPath(new URL('../',import.meta.url))){const result=[];
 function walk(relative){const full=path.join(root,relative);for(const entry of readdirSync(full,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name))){if(entry.isSymbolicLink()||['tests','test','fixtures','dist','node_modules','downloads','sources'].includes(entry.name))continue;const file=relative+'/'+entry.name;if(entry.isDirectory())walk(file);else if(/\.(html|php|mjs|js|blade\.php)$/.test(entry.name))result.push(...discoverForms(file,readFileSync(path.join(root,file),'utf8')));}}
 for(const r of roots)walk(r);return{version:1,scope:'Authored-source discovery candidates only; not a live or exhaustive native form audit.',sourceRoots:roots,excluded:['sources','tests','fixtures','dist','node_modules','downloads','symlinks'],runtimeReconciliationRequired:true,validationDimensions:dimensions,records:result.sort((a,b)=>a.source.localeCompare(b.source)||a.line-b.line)};
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){const root=fileURLToPath(new URL('../',import.meta.url)),file=path.join(root,'docs/form-audit-register.json'),next=JSON.stringify(inventory(root),null,2)+'\n';if(process.argv.includes('--check')){if(readFileSync(file,'utf8')!==next)throw Error('Form register is out of date; regenerate it.');console.log('Authored form register is reproducible and current.');}else{writeFileSync(file,next);console.log('Recorded '+JSON.parse(next).records.length+' source discovery candidates; none marked passed.');}}
