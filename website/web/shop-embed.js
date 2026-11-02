(()=>{
 const frame=document.querySelector('.dune-store-frame iframe');if(!frame)return;
 const childOrigin='https://cp.dunerealms.com';
 function requestSize(){frame.contentWindow?.postMessage({type:'dune-shop-measure'},childOrigin);}
 window.addEventListener('message',event=>{
  if(event.origin!==childOrigin||event.source!==frame.contentWindow||event.data?.type!=='dune-shop-size')return;
  const height=event.data.height;if(typeof height!=='number'||!Number.isFinite(height)||height<100||height>30000)return;
  frame.style.height=Math.max(600,Math.ceil(height))+'px';frame.setAttribute('scrolling','no');
 });
 frame.addEventListener('load',requestSize);
 const category=new URL(location.href).searchParams.get('category');
 if(['ranks','minecraft-hosting','web','entry-hosting','cloud-hosting','dedicated-hosting'].includes(category))frame.src=childOrigin+'/store/shop/categories/'+category;
 requestSize();
})();
