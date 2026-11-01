document.querySelectorAll('[data-tab]').forEach(button=>button.addEventListener('click',()=>{
 document.querySelectorAll('[data-tab]').forEach(tab=>tab.classList.toggle('active',tab===button));
 for(const id of ['details','evidence']){const panel=document.getElementById(id);panel.hidden=id!==button.dataset.tab;panel.classList.toggle('visible-tab',!panel.hidden);}
}));
