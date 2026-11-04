import{recordPageUpdates,menuBadgeScript}from'./menu-updates.mjs';
import {RELEASE_SCRIPT,RELEASE_CSS} from './release-branding.mjs';
import {BUTTON_CSS,BUTTON_SCRIPT} from './dune-buttons.mjs';
import {readFileSync,writeFileSync,readdirSync} from 'node:fs';
import {updatePortalNavigation,PORTAL_CSS,PORTAL_SCRIPT,CONTEXT_SCRIPT,CONTEXT_CSS} from './portal-navigation.mjs';
const web=new URL('../web/',import.meta.url);
writeFileSync(new URL('portal-navigation.css',web),PORTAL_CSS+CONTEXT_CSS+BUTTON_CSS+RELEASE_CSS);
recordPageUpdates();
writeFileSync(new URL('portal-navigation.js',web),PORTAL_SCRIPT+CONTEXT_SCRIPT+BUTTON_SCRIPT+RELEASE_SCRIPT+menuBadgeScript());
writeFileSync(new URL('dune-actions.css',web),BUTTON_CSS);
writeFileSync(new URL('dune-actions.js',web),BUTTON_SCRIPT);
for(const file of readdirSync(web).filter(f=>f.endsWith('.html'))){const url=new URL(file,web),before=readFileSync(url,'utf8'),after=updatePortalNavigation(before);if(after!==before)writeFileSync(url,after);}
