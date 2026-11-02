<?php
require 'client.inc.php';
foreach(Topic::getPublicHelpTopics() as $id=>$name){if($name==='Alpha Tester Signup'){header('Location: /open.php?topicId='.(int)$id, true,302);exit;}}
http_response_code(503);echo 'Alpha signup is temporarily unavailable. Please use the Support Center.';
