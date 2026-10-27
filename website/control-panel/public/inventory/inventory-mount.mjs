import {mountInventory} from './inventory-view.mjs';
const dashboard=document.getElementById('dashboard');
if(dashboard){
 const root=document.createElement('section');root.id='planetary-inventory';
 const game=document.getElementById('game');if(game)game.after(root);else dashboard.append(root);
 let view=null;
 const sync=()=>{if(dashboard.hidden){view?.clear();view=null;root.hidden=true;}else{root.hidden=false;if(!view)view=mountInventory(root);}};
 const changed=()=>{view?.clear();view=null;sync();};
 new MutationObserver(sync).observe(dashboard,{attributes:true,attributeFilter:['hidden']});
 for(const id of ['identity','game-status']){const target=document.getElementById(id);if(target)new MutationObserver(changed).observe(target,{childList:true,subtree:true,characterData:true});}
 window.addEventListener('dune:game-link-changed',changed);
 sync();
}
