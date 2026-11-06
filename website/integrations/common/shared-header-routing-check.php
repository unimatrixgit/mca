<?php
// Include definitions only; do not call the full native presentation/application bootstrap.
if(PHP_SAPI!=='cli'||$argc!==2)throw new RuntimeException('CLI usage: shared-header-routing-check.php <candidate-shared-header.php>');
require $argv[1];
$cases=[
 ['<script src="/dune-appeal-routing.js" defer></script>',true],
 ["<script defer src='/dune-appeal-routing.js'></script>",true],
 ["<SCRIPT\n SRC = '/dune-appeal-routing.js?v=2'></SCRIPT>",true],
 ['<script src="https://support.dunerealms.com/dune-appeal-routing.js"></script>',true],
 ['<script src="/dune-appeal-routing.js#helper"></script>',true],
 ['<html><head></head><body>No script</body></html>',false],
 ['<script src="/other.js"></script>',false],
 ['<script data-src="/dune-appeal-routing.js"></script>',false],
 ['<script src="https://other.invalid/dune-appeal-routing.js"></script>',false],
 ['<script src="/prefix-dune-appeal-routing.js"></script>',false],
 ['<script src="/dune-appeal-routing.js-extra"></script>',false],
 ['&lt;script src="/dune-appeal-routing.js"&gt;',false],
 ['Plain reference to /dune-appeal-routing.js',false],
 ['<scripture src="/dune-appeal-routing.js">',false],
 ["<script src=\"/dune-appeal-routing.js'></script>",false],
];
$checks=0;
foreach($cases as[$html,$expected]){
 $checks++;
 if(dune_has_appeal_routing_script($html)!==$expected)throw new RuntimeException('Header helper assertion '.$checks);
}
echo json_encode(['passed'=>$checks,'actualCandidateFunction'=>true,'applicationBootstrapped'=>false,'writes'=>0]).PHP_EOL;
