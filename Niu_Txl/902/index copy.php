<?php
ob_start();
session_start();

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
<title>802主页</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<style>
*{margin:0;padding:0;box-sizing:border-box;}
ul{list-style:none;margin:20px 0;}
li{margin:10px 0;text-align:center;}
a{text-decoration:none;color:#0066cc;}
a:hover{color:#ff0000;}
</style>
</head>
<body>

<div style="text-align:center; margin-top:30px;">
  <h1>
    <img src="message/images/logo1.png" alt="LOGO" width="40" height="40" />
    <font size="5">湖州市第五中学仁皇山校区八年级二班主页</font>
  </h1>
</div>

<div style="text-align:center; margin:10px 0;">
  <font size="3">
    <a href="https://baike.baidu.com/link?url=eeiBMNnfCOtVZViJucrCtbLdBK-nX3onfgkusTaFZSRZqyZEilKnR9ZxSjlqit79PR4yrK605TmBALm0_mPJekffwmzm0q3K7lYnSMH8JBRmJnt0vVSr0SylFQaNfeAEaMtYOLAnk2k6rTJDWOVp9eynXNYFHx5fRAKOIjbNM6C">
      ——了解湖州市第五中学教育集团——
    </a>
  </font>
</div>

<ul>
  <li><font size="5"><a href="message/index.php">大话班级</a></font></li>
  <li><font size="5"><a href="work/index.php">班级风采</a></font></li>
</ul>

<p style="text-align:center; margin-top:30px;">
  网站访问量：
  <img src="gd2.php" style="position:relative; top:4px; width:100px; height:20px;" alt="统计" />
</p>

</body>
</html>