import test from 'node:test';
import assert from 'node:assert/strict';
import {automatedMailTemplate} from '../node/automated-mail-templates.mjs';
test('ticket templates escape content and include a plain-text alternative',()=>{
 const mail=automatedMailTemplate('ticket-submitted',{ticketId:'DR_SP-0000008',subject:'<img src=x onerror=alert(1)>',recipientName:'Traveller',url:'https://support.dunerealms.com/tickets.php?id=123'});
 assert.ok(mail.html.includes('&lt;img'));assert.ok(!mail.html.includes('<img src=x'));assert.ok(mail.text.includes('DR_SP-0000008'));assert.ok(mail.html.includes('bgcolor="#0d211b"'));assert.ok(mail.html.includes('department-support-150.png'));
});
test('automatic messages cannot link to an unrelated or insecure host',()=>{
 for(const url of ['https://evil.example/','http://support.dunerealms.com/','https://user:pass@support.dunerealms.com/'])assert.throws(()=>automatedMailTemplate('new-ticket-alert',{url}));
});
