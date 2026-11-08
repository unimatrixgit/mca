// Input is trusted game state, never profile fields or names supplied by a browser.
export const officeNames=Object.freeze({emperor:'Emperor',house_leader:'Faction leader',specerhuis:'Specerhuis',welvaart:'House of Water',guild_staff:'Vaerkring',landsraad:'Landsraad'});
export const houseKey=value=>({atreides:'aerdrecht',aerdrecht:'aerdrecht',aedrecht:'aerdrecht',harkonnen:'harkveen',harkveen:'harkveen',corrino:'korenholt',korenholt:'korenholt',ordos:'oordwijk',oordwijk:'oordwijk',fremen:'vrijduin',vrijduin:'vrijduin',guild:'vaerkring',spacing_guild:'vaerkring',vaerkring:'vaerkring'})[String(value||'').toLowerCase()]||'';
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function winner(election){
 return Object.entries(election?.tally||{}).filter(([id,n])=>uuid.test(id)&&Number.isInteger(n)&&n>0).sort((a,b)=>b[1]-a[1]||a[0].localeCompare(b[0]))[0]?.[0]||'';
}
export function interimWinner(state,kind,faction,now){const entry=kind==='emperor'?state?.interim?.emperor:state?.interim?.house?.[faction];return entry&&uuid.test(entry.uuid||'')&&Number.isSafeInteger(entry.startsAt)&&Number.isSafeInteger(entry.expiresAt)&&entry.startsAt<=now&&now<entry.expiresAt?entry.uuid.toLowerCase():'';}
export function officesFor(state,player,now=Date.now()){
 if(!uuid.test(player.uuid))throw Error('Verified Minecraft UUID required');
 const week=Math.floor(now/604800000),id=player.uuid.toLowerCase(),faction=String(player.faction||''),house=houseKey(faction),roles=[];
 const emperor=interimWinner(state,'emperor',faction,now)||winner(state?.votes?.emperor?.[week]);
 if(emperor.toLowerCase()===id)roles.push('emperor');
 // The stored election key is the game's faction name, matching Core Politics.
 if(faction&&(interimWinner(state,'house',faction,now)||winner(state?.votes?.house?.[week]?.[faction])).toLowerCase()===id)roles.push('house_leader');
 for(const [key,role]of [['steward','specerhuis'],['water','welvaart']]){
  const a=state?.appointments?.[key];
  if(emperor&&a?.week===week&&a.emperor===emperor&&faction&&faction.toLowerCase()===String(a.house||'').toLowerCase())roles.push(role);
 }
 if(house==='vaerkring')roles.push('guild_staff');
 if(roles.length)roles.push('landsraad');
 return{uuid:id,house,roles,expiresAt:(week+1)*604800000};
}
export function mergeManagedGroups(existing,managed,desired){
 const remove=new Set(managed.map(Number));
 return [...new Set([...String(existing||'').split(',').map(Number).filter(n=>n>0&&!remove.has(n)),...desired.map(Number)])].sort((a,b)=>a-b).join(',');
}
