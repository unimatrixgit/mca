import {readFileSync,writeFileSync,copyFileSync} from 'node:fs';
const root=process.argv[2]||'/home/dunerealms/apps/integrations';
const source=process.argv[3]||new URL('../integrations/',import.meta.url).pathname;
const helper=root+'/shared-header.php';let php=readFileSync(helper,'utf8');
if(!php.includes('dune_native_seo($html)')){
 if(!php.includes(' return $html;'))throw Error('Shared header return anchor missing');
 copyFileSync(helper,helper+'.before-native-seo');
 php=php.replace(' return $html;'," require_once __DIR__.'/native-seo.php';\n return dune_native_seo($html);");
 writeFileSync(helper,php);
}
for(const [src,dst]of [['common/native-seo.php','native-seo.php'],['osticket/robots.txt','osticket/robots.txt'],['osticket/sitemap.xml','osticket/sitemap.xml']])copyFileSync(source+'/'+src,root+'/'+dst);
console.log('Native public support metadata and sitemap installed; private native pages marked noindex.');
