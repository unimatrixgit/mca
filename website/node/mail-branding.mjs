import{staffClosureStats,statsForAccount,departmentAwardEarned}from './staff-closure-stats.mjs';
import {readFile} from 'node:fs/promises';
import {accessFor} from './permissions.mjs';
import {staffSealFor,departmentsFor,visibleCommunityBadges} from './native-seals.mjs';
import {badgesFor} from './community-badges.mjs';
export async function mailBrandingFor(db,user){
 const stats=statsForAccount(await staffClosureStats(db),user.id);
 const access=await accessFor(db,user),manifest=JSON.parse(await readFile(new URL('../web/assets/seals/manifest.json',import.meta.url),'utf8')),seals=[];
 const add=(path,label)=>{if(path&&!seals.some(s=>s.url.endsWith(path)))seals.push({url:'https://www.dunerealms.com'+path,label});};
 add(staffSealFor({...user,ranks:access.ranks},manifest),access.label);
 if(access.ranks.includes('web_streamer'))add('/assets/seals/rank-streamer-150.png','Streamer');
 for(const department of departmentsFor(access.nodes,access.ranks,user.username)){add('/assets/seals/department-'+(department==='legal'?'legal-v2':department)+'-150.png',department.replaceAll('-',' ')+' department');if(departmentAwardEarned(stats,department))add('/assets/seals/team-'+department+'.png',department.replaceAll('-',' ')+' team');}
 const community=JSON.parse(await readFile(new URL('../web/assets/seals/community-manifest.json',import.meta.url),'utf8'));
 for(const badge of visibleCommunityBadges(await badgesFor(db,user.id),access.ranks))add(community[badge]?.footer150||community[badge]?.src,badge.replaceAll('-',' '));
 return {rankLabel:access.label,seals};
}
