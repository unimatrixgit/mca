<?php
// CLI-only, repeatable native entry-point integration. No core validation removed.
if(PHP_SAPI!=='cli')exit(1);
$path=__DIR__.'/open.php';
$text=file_get_contents($path);
$anchor="require('client.inc.php');";
$hook="\nrequire_once __DIR__.'/alpha-intake.php';\ndune_alpha_intake(\$_POST['topicId'] ?? \$_GET['topicId'] ?? 0);";
if(str_contains($text,"dune_alpha_intake(")){echo "Already installed\n";exit;}
if(substr_count($text,$anchor)!==1)throw new RuntimeException('Unexpected native entry point');
file_put_contents($path,str_replace($anchor,$anchor.$hook,$text));
echo "Installed Alpha intake integration\n";
