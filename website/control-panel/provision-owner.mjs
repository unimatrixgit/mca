import {readFile,unlink} from 'node:fs/promises';
import {randomUUID,randomBytes,scrypt as rawScrypt} from 'node:crypto';import {promisify} from 'node:util';
import {openDatabase} from '../node/mysql.mjs';
const file=process.argv[2];if(!file||!file.startsWith('/tmp/'))throw Error('Use a private bootstrap JSON file in /tmp.');
const input=JSON.parse(await readFile(file,'utf8'));if(!/^[a-z0-9_]{3,32}$/.test(input.username)||typeof input.password!=='string'||input.password.length<24)throw Error('Invalid owner bootstrap.');
const db=await openDatabase(process.env);try{
 const id=randomUUID(),now=Date.now(),salt=randomBytes(16).toString('hex'),hash=(await promisify(rawScrypt)(input.password,salt,64)).toString('hex');
 await db.batch([db.prepare('INSERT INTO site_accounts(id,username,display_name,password_salt,password_hash,role,created_at) VALUES(?,?,?,?,?,?,?)').bind(id,input.username,'Unimatrix',salt,hash,'staff',now),db.prepare('INSERT INTO player_profiles(user_id,display_name,minecraft_name,discord_name,created_at,updated_at) VALUES(?,?,?,?,?,?)').bind(id,'Unimatrix','','',now,now),db.prepare('INSERT INTO account_audit(id,actor_id,action,details,created_at) VALUES(?,?,?,?,?)').bind(randomUUID(),id,'owner-provisioned','Explicit owner request; private bootstrap',now)]);
 await unlink(file);console.log('Owner staff account provisioned; bootstrap removed.');
}finally{await db.close();}
