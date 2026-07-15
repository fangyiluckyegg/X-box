<?php
if(!isset($_SESSION)){
    session_start();
}
if (empty($_SESSION["temp"])) { 
    if (($fp = fopen("counter.txt","r")) == False) {
        echo "打开文件失败";
    } else {
        $counter = fgets ( $fp , 1024 );
        fclose( $fp );
        $counter ++;
        $fp = fopen ("counter.txt","w");
        fputs ($fp, $counter );
        fclose( $fp );
    }
    $_SESSION["temp"] = 1;
}
?>


<!DOCTYPE html>
<html>
<head>
<title>永远的高三2班</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"></head>
<body>
    <div class="text" style=" text-align:center;">
        <h1><img src="message/images/logo1.png" alt="图片无法显示" width="40" height="40" /><font size="25">湖州市第五中学仁皇山校区高三2班主页</font></h1>
    </div>
    <div class="text" style=" text-align:center;">
        <font size="5"><a href="https://baike.baidu.com/link?url=eeiBMNnfCOtVZViJucrCtbLdBK-nX3onfgkusTaFZSRZqyZEilKnR9ZxSjlqit79PR4yrK605TmBALm0_mPJekffwmzm0q3K7lYnSMH8JBRmJnt0vVSr0SylFQaNfeAEaMtYOLAnk2k6rTJDWOVp9eynXNYFHx5fRAKOIjbNM6C">——了解湖州市第五中学教育集团——</a></font>
    </div>
<ul>
    <li><font size="10"><a href="message/index.php">大话班级</a></font></li>
	<li><font size="10"><a href="work/index.php">班级风采</a></font></li>

</ul>
<p align="center">网站访问量：<font color="red" size="4"><b><?php echo file_get_contents("counter.txt"); ?></b></font></p>
</body>
</html>