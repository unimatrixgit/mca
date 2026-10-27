import {readFileSync,writeFileSync,readdirSync} from 'node:fs';

const notice='<aside class="legal-notice" aria-label="Unofficial fan project notice"><p><strong>Unofficial fan project</strong></p><p>dunerealms.com is an independently operated fan project. It is not affiliated with, endorsed, sponsored or approved by the owners of the Dune books, films, games or associated intellectual property. Dune names, characters, settings and trademarks belong to their respective rights holders. No ownership of those rights is claimed.</p><p>NOT AN OFFICIAL MINECRAFT SERVER OR PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT. The dunerealms.com operators are responsible for this website and server.</p><p>Operator contact, support and rights-holder enquiries: <span class="operator-contact"><button type="button" class="contact-reveal">Show contact email</button><noscript>Enable JavaScript to complete contact verification.</noscript></span>.</p><p><a href="wiki.html">Full disclaimer &amp; world guide →</a></p></aside>';
const web=new URL('../web/',import.meta.url);
const pages=readdirSync(web).filter(file=>file.endsWith('.html')).map(file=>new URL(file,web));
pages.push(new URL('../node/public/login.html',import.meta.url));
for(const page of pages){
  let html=readFileSync(page,'utf8').replace(/<aside class="legal-notice"[\s\S]*?<\/aside>/g,'');
  html=html.includes('<footer>')?html.replace('<footer>',notice+'<footer>'):html.replace('</body>',notice+'</body>');
  html=html.replace(/<a href="donate.html" class="community-support-link">Community support<\/a>/g,'').replace('<footer>','<footer><a href="donate.html" class="community-support-link">Community support</a>');
  html=html.replace(/<script src="public-names.js" defer><\/script>/g,'').replace('<head>','<head><script src="public-names.js" defer></script>');
  html=html.replace(/<a href="wiki.html" class="wiki-link">(?:World wiki|Disclaimer)<\/a>/g,'');
  html=html.replace(/<script type="module" src="contact.js"><\/script>/g,'').replace(/<link rel="stylesheet" href="assets\/altcha.css">/g,'');
  html=html.replace('</head>','<link rel="stylesheet" href="assets/altcha.css"><script type="module" src="contact.js"></script></head>');
  writeFileSync(page,html);
}
console.log('Built visible fan-project notices on',pages.length,'pages');
