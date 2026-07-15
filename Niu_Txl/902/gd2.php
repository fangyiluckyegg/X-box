<?php
// 强制清空输出
@ob_clean();

// 读取计数
$num = trim(file_get_contents("counter.txt"));
$num = $num ?: "0";

// -----------------------
// 关键：检查是否支持 GD，不支持就直接输出文字图片
// -----------------------
if (!function_exists('imagecreate')) {
    header("Content-Type: image/png");
    // 输出一个1x1透明图片，防止报错
    echo base64_decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABAQAAAAA3bvkkAAAACklEQVQI12NgAAAAAgAB4iG8MwAAAABJRU5ErkJggg==');
    exit;
}

// 以下是正常图片生成
$img = imagecreate(80, 24);
$white = imagecolorallocate($img, 255, 255, 255);
$red = imagecolorallocate($img, 255, 0, 0);

imagestring($img, 5, 8, 4, $num, $red);

header("Content-Type: image/png");
imagepng($img);
imagedestroy($img);
?>