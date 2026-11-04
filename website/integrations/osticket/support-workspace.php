<?php
// Presentation only. Never reads ticket data or changes native authorisation.
function dune_support_workspace(string $html, string $path): string {
    if (stripos($html, '<html') === false || str_contains($html, 'dune-support-workspace-hero')
        || preg_match('~/(dev-tasks|dev-view)\.php$~', $path)) return $html;
    $staff = str_starts_with($path, '/scp/');
    $page = basename($path);
    $title = $staff ? 'Staff workspace' : 'Support Center';
    $description = $staff ? 'Your casework, conversations and team tools in one place.' : 'Help for your account, your adventures and your community.';
    $art = $staff ? 'seals/department-network-staff-150.png' : 'nav-support-full-v2.webp';
    if ($page === 'tickets.php') {
        $title = $staff ? 'Ticket desk' : 'Your support tickets';
        $description = $staff ? 'Review assigned cases and respond through the original ticket controls below.' : 'Follow your requests and continue the conversation with Support.';
    } elseif (in_array($page, ['open.php','alpha-signup.php','appeal.php'], true)) {
        $title = ['open.php'=>'Start a support request','alpha-signup.php'=>'Become an Alpha tester','appeal.php'=>'Request a ban review'][$page];
        $description = 'Complete the form below. Your request is private to you and authorised staff.';
        if ($page === 'alpha-signup.php') $art = 'news-community.webp';
        if ($page === 'appeal.php') $art = 'seals/department-ban-appeals-150.png';
    } elseif ($page === 'view.php') {
        $title = 'Your ticket conversation';
        $description = 'Review the conversation, evidence and updates, then reply below.';
    } elseif ($page === 'login.php') {
        $title = $staff ? 'Staff sign in' : 'Access your support requests';
        $description = 'Use the existing secure sign-in options below.';
    }
    $esc = static fn(string $s): string => htmlspecialchars($s, ENT_QUOTES, 'UTF-8');
    $hero = '<section class="dune-support-workspace-hero" aria-label="'.($staff?'Staff workspace':'Support workspace').'">'
        .'<img src="https://www.dunerealms.com/assets/'.$esc($art).'" width="112" height="112" alt="">'
        .'<div><p class="dune-workspace-eyebrow">DUNEREALMS · '.($staff?'STAFF OPERATIONS':'COMMUNITY SUPPORT').'</p>'
        .'<h1>'.$esc($title).'</h1><p>'.$esc($description).'</p></div></section>';
    // Only the native content root; forms, messages and navigation are byte-preserved.
    $html = preg_replace_callback('~<div\b[^>]*\bid=["\']content["\'][^>]*>~i', static fn($m) => $m[0].$hero, $html, 1);
    $html = preg_replace_callback('~<body\b([^>]*)>~i', static function($m) use ($staff) {
        $classes = 'dune-support-workspace '.($staff?'dune-support-staff':'dune-support-client');
        $attrs = $m[1];
        if (preg_match('~\bclass=(["\'])(.*?)\1~i', $attrs)) $attrs = preg_replace('~\bclass=(["\'])(.*?)\1~i', 'class="$2 '.$classes.'"', $attrs, 1);
        else $attrs .= ' class="'.$classes.'"';
        return '<body'.$attrs.'>';
    }, $html, 1);
    return str_ireplace('</head>', '<link rel="stylesheet" href="/dune-support-workspace.css?v=364-1"><script src="/dune-support-workspace.js?v=364-1" defer></script></head>', $html);
}
