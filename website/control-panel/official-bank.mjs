import {identity} from '../node/auth.mjs';
import {accountNumber} from './imperial-accounts.mjs';
const reply=(body,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'private, no-store',Vary:'Cookie'}});
export function createOfficialBank(db,settings={},deps={}){
 const auth=deps.authenticate||(r=>identity(r,db));
 const assigned=deps.assigned||(async u=>db.prepare("SELECT b.id FROM imperial_bank_accounts b JOIN imperial_bank_account_admins a ON a.account_id=b.id WHERE b.bank_type='server' AND b.owner_key='mcserver' AND a.user_id=?").bind(u.id).first());
 return async request=>{
  if(request.method!=='GET'||new URL(request.url).search)return reply({message:'Read-only account; account overrides are not accepted.'},400);
  const user=await auth(request);if(!user)return reply({message:'Sign in first.'},401);const grant=await assigned(user);if(!grant)return reply({accounts:[]});
  try{const r=await(deps.fetcher||fetch)('http://127.0.0.1:15433/bank/mcserver',{headers:{Authorization:'Bearer '+settings.SERVER_CONTROL_TOKEN},signal:AbortSignal.timeout(8000)});if(!r.ok)throw Error();const bank=await r.json();
   if(bank.accountNumber!==accountNumber('server',grant.id)||typeof bank.balanceDunemarks!=='string'||!/^\d+\.\d{2}$/.test(bank.balanceDunemarks)||!Array.isArray(bank.ledger)||bank.ledger.length>100)throw Error();
   const current=await auth(request);if(!current||current.id!==user.id||!await assigned(current))return reply({message:'Account access changed.'},403);
   return reply({accounts:[{name:'MCserveraccount',bank:{accountNumber:bank.accountNumber,balanceDunemarks:bank.balanceDunemarks,ledger:bank.ledger.map(r=>({id:r.id,action:r.action,amountDunemarks:r.amountDunemarks,balanceDunemarks:r.balanceDunemarks,createdAt:r.createdAt}))},canManage:true}]});
  }catch{return reply({message:'MCserveraccount is temporarily unavailable.'},503);}
 };
}
