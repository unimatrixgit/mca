import test from 'node:test';
import assert from 'node:assert/strict';
import {accountNumber,validAccountNumber,imperialAccount} from '../control-panel/imperial-accounts.mjs';
test('four type formats, reserved server prefix and checksum',()=>{
 for(const [type,code,prefix] of [['player','PL','101'],['clan','CL','201'],['faction','FA','301'],['server','SV','979']]){
  const n=accountNumber(type,127);assert.match(n,new RegExp('^DR'+code+' '+prefix));assert.equal(n.length,19);assert.ok(validAccountNumber(n));
  const raw=n.replaceAll(' ','');for(let i=4;i<16;i++){const changed=raw.slice(0,i)+(Number(raw[i])+1)%10+raw.slice(i+1);assert.equal(validAccountNumber(changed),false);}
 }
});
test('invalid types, overflow, zero and wrong owner are rejected',async()=>{
 for(const id of [0,-1,10000000,1.1,NaN])assert.throws(()=>accountNumber('player',id));assert.throws(()=>accountNumber('unknown',1));
 for(const [t,o]of [['player','alice'],['clan','test'],['faction','fake'],['server','other']])await assert.rejects(imperialAccount({},t,o));
 assert.equal(validAccountNumber('DRPL 9790 0000 0001'),false);
});
