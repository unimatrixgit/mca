<?php
if(PHP_SAPI!=='cli')exit(1);
define('ROOT_DIR',dirname(__DIR__));require($argv[1]??dirname(__DIR__,2).'/managed-signatures.php');
$checks=0;function verify_seal($value){global$checks;if(!$value)throw new RuntimeException('Seal assertion '.($checks+1).' failed');$checks++;}
verify_seal(!dune_department_personal_earned(null,'support'));
verify_seal(!dune_department_personal_earned(['departments'=>['support'=>24]],'support'));
verify_seal(dune_department_personal_earned(['departments'=>['support'=>25]],'support'));
verify_seal(dune_department_personal_earned(['departments'=>['support'=>26]],'support'));
verify_seal(!dune_department_personal_earned(['departments'=>['legal'=>25]],'support'));
$a=['website_id'=>'00000000-0000-4000-8000-00000000fa25','username'=>'unimatrix999','personal_path'=>'/assets/seals/owner-unimatrix999-150.png','departments_json'=>'["support","support"]','teams_json'=>'["support"]'];
[$role,$departments]=dune_signature_staff_badges($a,['departments'=>['support'=>24]]);
verify_seal(count($role)===1);verify_seal(count($departments)===1);verify_seal(str_contains($departments[0][0],'department-support-150.png'));verify_seal(!str_contains($departments[0][0],'personal-department'));
[$role,$departments]=dune_signature_staff_badges($a,['departments'=>['support'=>25]]);
verify_seal(count($role)===1);verify_seal(count($departments)===1);verify_seal(str_contains($departments[0][0],'earned/personal-department-support-'));verify_seal(str_contains($departments[0][1],'unimatrix999'));
verify_seal(!str_contains($departments[0][0],$a['website_id']));
$main=$a;$main['username']='grmacken';$main['personal_path']='/assets/seals/main-network-admin-grmacken-150.png';
[, $departments]=dune_signature_staff_badges($main,['departments'=>['support'=>0]]);verify_seal(count($departments)===1&&!str_contains($departments[0][0],'personal-department'));
[, $departments]=dune_signature_staff_badges($a,null);verify_seal(count($departments)===1&&!str_contains($departments[0][0],'personal-department'));
echo "$checks department-seal assertions passed\n";
