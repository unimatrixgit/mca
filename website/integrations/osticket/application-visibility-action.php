<?php
require 'staff.inc.php';require_once dirname(__DIR__).'/application-visibility.php';
if(($_SERVER['REQUEST_METHOD']??'')!=='POST'){http_response_code(405);exit;}
$_SESSION['dune_show_applications']=($_POST['show_applications']??'')==='1';session_write_close();header('Location: /scp/tickets.php',true,303);exit;
