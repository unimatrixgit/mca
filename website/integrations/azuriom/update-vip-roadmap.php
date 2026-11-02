<?php
// Run as application owner from the Azuriom root. Copy-only, guarded migration.
require 'vendor/autoload.php';
$app = require 'bootstrap/app.php';
$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();
use Illuminate\Support\Facades\DB;

$replacement = trim(file_get_contents(__DIR__.'/vip-cosmetic-roadmap.html'));
$start = '<h3>On the horizon — planned for the next Alpha</h3>';
$end = '<p><strong>Current price:';
$backup = $argv[1] ?? '';
if (!is_dir($backup) || !is_writable($backup)) throw new RuntimeException('Private writable backup directory required');
DB::transaction(function () use ($replacement, $start, $end, $backup) {
    $p = DB::table('shop_packages')->where('id', 1)->lockForUpdate()->first();
    if (!$p || $p->name !== 'VIP' || (float)$p->price !== 50.0 || $p->dune_website_rank !== 'vip') {
        throw new RuntimeException('Unexpected package identity, rank or price');
    }
    if (str_contains($p->description, $replacement)) { echo "Already applied\n"; return; }
    $a = strpos($p->description, $start);
    $b = strpos($p->description, $end);
    if ($a === false || $b === false || $a >= $b) throw new RuntimeException('Unexpected description; manual review required');
    $oldSection = substr($p->description, $a, $b-$a);
    if (!str_contains($oldSection, 'Three complimentary Upgrade Token codes')) throw new RuntimeException('Unexpected roadmap');
    $snapshot = json_encode($p, JSON_PRETTY_PRINT|JSON_THROW_ON_ERROR);
    $file = $backup.'/vip-package-before.json';
    $handle = fopen($file, 'x');
    if (!$handle) throw new RuntimeException('Backup must not be overwritten');
    chmod($file, 0600);
    if (fwrite($handle, $snapshot) !== strlen($snapshot)) throw new RuntimeException('Incomplete backup');
    fclose($handle);
    $description = substr($p->description, 0, $a).$replacement.substr($p->description, $b);
    // Query builder deliberately bypasses package reward hooks. No receipt, role or command changes.
    DB::table('shop_packages')->where('id', 1)->update(['description'=>$description]);
    echo "VIP roadmap updated; all other package fields preserved\n";
});
