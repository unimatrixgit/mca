<?php
// Navigation only. Existing SSO nonce, identity and native form authorisation remain authoritative.
function dune_public_intake_path($candidate) {
    if (!is_string($candidate) || strlen($candidate)>128) return null;
    if ($candidate==='/alpha-signup.php' || $candidate==='/open.php') return $candidate;
    if (!preg_match('~^/open\.php\?topicId=([1-9][0-9]{0,8})$~D',$candidate,$match)) return null;
    $public=Topic::getPublicHelpTopics();
    return is_array($public) && array_key_exists((int)$match[1],$public) ? $candidate : null;
}
function dune_requested_intake_path($file, $query, $method) {
    if (!in_array($method,['GET','HEAD'],true)) return null;
    if ($file==='alpha-signup.php') return '/alpha-signup.php';
    if ($file!=='open.php') return null;
    if (!array_key_exists('topicId',$query)) return '/open.php';
    if (!is_string($query['topicId'])) return null;
    return dune_public_intake_path('/open.php?topicId='.$query['topicId']);
}
function dune_remember_intake_path(&$session, $query, $now, $method='GET') {
    unset($session['dune_intake_return']);
    if (!in_array($method,['GET','HEAD'],true)) return;
    // Explicit staff, Dev and appeal flows retain their existing destinations.
    if (($query['staff']??'')==='1' || ($query['admin']??'')==='1' || ($query['dev']??'')==='1' || array_key_exists('appeal',$query)) return;
    $path=dune_public_intake_path($query['intake']??null);
    if ($path!==null) $session['dune_intake_return']=['path'=>$path,'expires'=>$now+300];
}
function dune_take_intake_path(&$session, $now) {
    $saved=$session['dune_intake_return']??null;unset($session['dune_intake_return']);
    if (!is_array($saved) || !is_int($saved['expires']??null) || $saved['expires']<=$now || $saved['expires']>$now+300) return null;
    // Recheck the current catalogue: a topic may have become private during sign-in.
    return dune_public_intake_path($saved['path']??null);
}
