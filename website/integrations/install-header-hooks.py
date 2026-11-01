from pathlib import Path
import re,sys
base=Path(sys.argv[1])
p=base/'mybb/inc/plugins/dune_accounts.php';s=p.read_text()
replacement="function dune_accounts_theme($html){require_once '/home/dunerealms/apps/integrations/shared-header.php';return dune_shared_header(str_replace('</head>','<link rel=\"stylesheet\" href=\"/dune-theme.css\"></head>',$html));}"
s,count=re.subn(r'function dune_accounts_theme\(\$html\)\{[^\n]*\}',lambda _:replacement,s,count=1)
if count!=1:raise RuntimeError('MyBB theme hook missing')
needle='function dune_accounts_admin(){'
if "ob_start('dune_shared_header');" not in s:s=s.replace(needle,needle+"\n require_once '/home/dunerealms/apps/integrations/shared-header.php';ob_start('dune_shared_header');",1)
p.write_text(s)
p=base/'osticket/include/plugins/dunerealms/authenticate.php';s=p.read_text()
needle="  if(PHP_SAPI==='cli')return;"
if "ob_start('dune_shared_header');" not in s:
 if needle not in s:raise RuntimeError('osTicket bootstrap hook missing')
 s=s.replace(needle,needle+"\n  require_once '/home/dunerealms/apps/integrations/shared-header.php';ob_start('dune_shared_header');",1)
p.write_text(s)
if len(sys.argv)>2:
 p=Path(sys.argv[2]);s=p.read_text()
 if 'class="dune-site-header"' not in s:
  header=(base/'shared-header.html').read_text();head=(base/'shared-header-head.html').read_text()
  s=s.replace('<html lang="en">','<html lang="en"><head>',1).replace('<main>','</head><body>'+header+'<main>',1).replace('</html>','</body></html>')
  s=s.replace('</head>',head+'</head>',1)
  p.write_text(s)
print('Native header hooks installed')
