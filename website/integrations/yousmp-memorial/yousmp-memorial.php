<?php
/** Plugin Name: YouSMP Sir Patrick Stewart Memorial */
if (!defined('ABSPATH')) exit;
add_action('wp_enqueue_scripts', function () {
 $base=content_url('/yousmp-memorial/');
 wp_enqueue_style('yousmp-stewart-memorial',$base.'memorial.css',[], '1.0.0');
 wp_enqueue_script('yousmp-stewart-memorial',$base.'memorial.js',[], '1.0.0',true);
});
