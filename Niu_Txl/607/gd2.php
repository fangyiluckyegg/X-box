<?php
header ("content-type:image/png");
if(($fp=fopen("counter.txt","r"))==False){
    echo"打开文件失败";
}else{
    $counter=fgets($fp,1024);
   // echo $counter;
    fclose($fp);
    $im=imagecreate(50,20);
    $gray=imagecolorallocate($im,255,255,255);
    $color=imagecolorallocate($im,255,0,0);
    imagestring($im,8,2,2,$counter,$color);
    imagepng($im);
    imagedestroy($im);
}
?>