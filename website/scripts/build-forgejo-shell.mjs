import {SHARED_HEADER,SHARED_HEAD} from './portal-navigation.mjs';
import {mkdirSync,writeFileSync} from 'node:fs';
const path=new URL('../integrations/forgejo/templates/custom/',import.meta.url);
mkdirSync(path,{recursive:true});
writeFileSync(new URL('header.tmpl',path),SHARED_HEAD+'\n<link rel="stylesheet" href="https://www.dunerealms.com/git/assets/css/theme-dunerealms.css?v=6">\n<script src="https://www.dunerealms.com/git/assets/js/dune-shell.js?v=1" defer></script>\n');
writeFileSync(new URL('body_outer_pre.tmpl',path),SHARED_HEADER+'\n');
writeFileSync(new URL('footer.tmpl',path),'<div class="dune-git-footer-note">DuneGit · Build, collaborate and share with the caravan.</div>\n');
