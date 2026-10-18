import {request as httpRequest} from 'node:http';
export function createForgejoProxy(settings){
 if(!settings.FORGEJO_URL)return ()=>false;
 const upstream=new URL(settings.FORGEJO_URL),origin=new URL(settings.PUBLIC_ORIGIN||'https://dunerealms.com');
 if(upstream.protocol!=='http:'||!['127.0.0.1','localhost'].includes(upstream.hostname))throw Error('Forgejo must bind to localhost');
 return (req,res)=>{
  if(req.url==='/git'||req.url?.startsWith('/git?')){res.writeHead(308,{Location:'/git/'+(req.url.slice(4)||''),'Cache-Control':'no-store'});res.end();return true;}
  if(!req.url?.startsWith('/git/'))return false;
  const headers={...req.headers,host:origin.host,'x-forwarded-proto':origin.protocol.slice(0,-1),'x-forwarded-host':origin.host,'x-forwarded-for':req.socket.remoteAddress};
  for(const key of Object.keys(headers))if(key.startsWith('oai-')||key.startsWith('x-internal-')||key.startsWith('x-webauth-')||['connection','upgrade','proxy-authorization'].includes(key))delete headers[key];
  if(headers.cookie)headers.cookie=headers.cookie.split(';').filter(c=>!c.trim().startsWith('__Host-dune_session=')).join(';');
  req.setTimeout(300000);
  const request=httpRequest({hostname:upstream.hostname,port:upstream.port,method:req.method,path:req.url.slice(4),headers},response=>{
   const out={...response.headers};delete out.connection;res.writeHead(response.statusCode||502,out);response.pipe(res);response.on('error',()=>res.destroy());
  });
  request.setTimeout(300000,()=>request.destroy());
  request.on('error',()=>{if(!res.headersSent)res.writeHead(502,{'Content-Type':'text/plain','Cache-Control':'no-store'});res.end('The code forge is temporarily unavailable.');});
  res.on('close',()=>{if(!res.writableEnded)request.destroy();});req.on('aborted',()=>request.destroy());req.pipe(request);return true;
 };
}
