<?php
// Install as index.php, retaining native osTicket session, header and footer.
require('client.inc.php');
$section='home';
require(CLIENTINC_DIR.'header.inc.php');
require __DIR__.'/dune-support-home.php';
require(CLIENTINC_DIR.'footer.inc.php');
