import {RELEASE_SCRIPT,RELEASE_CSS} from './release-branding.mjs';
import {BUTTON_CSS,BUTTON_SCRIPT} from './dune-buttons.mjs';
import {existsSync, readFileSync,writeFileSync,copyFileSync} from 'node:fs';
import {SHARED_HEADER,SHARED_HEAD,PORTAL_SCRIPT,PORTAL_CSS,CONTEXT_SCRIPT,CONTEXT_CSS} from './portal-navigation.mjs';
const release=process.argv[2],integrations=process.argv[3];
if(!release||!integrations)throw Error('Provide release and integration directories');
const cp=release+'/control-panel/public/index.html';let html=readFileSync(cp,'utf8');
html=html.replace(/<header\b[^>]*>[\s\S]*?<\/header>/,SHARED_HEADER);
if(!html.includes('https://www.dunerealms.com/portal-navigation.js'))html=html.replace('</head>',SHARED_HEAD+'</head>');
writeFileSync(cp,html);
writeFileSync(integrations+'/shared-header.html',SHARED_HEADER);
writeFileSync(integrations+'/shared-header-head.html',SHARED_HEAD);
copyFileSync(release+'/integrations/common/shared-header.php',integrations+'/shared-header.php');
copyFileSync(release+'/integrations/osticket/dune-support.css',integrations+'/osticket/dune-support.css');
for(const app of ['mybb','osticket']){
 writeFileSync(integrations+'/'+app+'/dune-actions.css',BUTTON_CSS);
 writeFileSync(integrations+'/'+app+'/dune-actions.js',BUTTON_SCRIPT);
 writeFileSync(integrations+'/'+app+'/dune-navigation.js',PORTAL_SCRIPT+CONTEXT_SCRIPT+BUTTON_SCRIPT+RELEASE_SCRIPT);
 writeFileSync(integrations+'/'+app+'/dune-navigation.css',PORTAL_CSS+CONTEXT_CSS+BUTTON_CSS+RELEASE_CSS);
}
let server=readFileSync(release+'/control-panel/server.mjs','utf8');
server=server.replace("script-src 'self'; style-src 'self' https://fonts.googleapis.com;", "script-src 'self' https://www.dunerealms.com; style-src 'self' https://www.dunerealms.com https://fonts.googleapis.com;");
server=server.replace("img-src 'self' data:; frame-src", "img-src 'self' data: https://www.dunerealms.com; frame-src");
writeFileSync(release+'/control-panel/server.mjs',server);
console.log('Shared header installed in CP and native integration templates');

// The Clan center serves local copies to retain the same masthead styles.
const clans='/home/dunerealms/htdocs/clans.dunerealms.com';
if(existsSync(clans+'/index.html')){writeFileSync(clans+'/dune-navigation.css',PORTAL_CSS+CONTEXT_CSS+BUTTON_CSS+RELEASE_CSS);writeFileSync(clans+'/dune-navigation.js',PORTAL_SCRIPT+CONTEXT_SCRIPT+BUTTON_SCRIPT+RELEASE_SCRIPT);}
