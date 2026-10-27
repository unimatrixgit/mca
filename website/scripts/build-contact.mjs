import {copyFileSync,readFileSync,writeFileSync} from 'node:fs';
const root=new URL('../',import.meta.url);
for(const [source,target] of [['dist/external/altcha.min.js','web/assets/altcha.js'],['dist/workers/pbkdf2.js','web/assets/altcha-pbkdf2.js'],['dist/external/altcha.css','web/assets/altcha.css']])copyFileSync(new URL('node_modules/altcha/'+source,root),new URL(target,root));
writeFileSync(new URL('ALTCHA-LICENSE.txt',root),readFileSync(new URL('node_modules/altcha/LICENSE.txt',root)));
