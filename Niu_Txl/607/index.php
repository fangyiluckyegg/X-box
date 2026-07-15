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
        //echo $counter;
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
<meta charset="utf-8">
<title>永远的607</title>
</head>
<body>
<script type="text/javascript" src="/607/index.php"></script> 
<h1><img src="/607/永远的607/爱山校徽.png" alt="图片无法显示" width="100" height="100" />你好,这里是中华人民共和国浙江省湖州市吴兴区爱山小学教育集团仁皇校区六年级7班的纪念网站</h1>
<a href="https://baike.baidu.com/item/%E6%B9%96%E5%B7%9E%E5%B8%82%E7%88%B1%E5%B1%B1%E5%B0%8F%E5%AD%A6/15395194?fr=aladdin">了解湖州市吴兴区爱山小学教育集团——</a>
<p>我们于2016年9月进入了爱山这个大家庭,到现在我们已经一起走过了6年的小学生活,我们在6年里一起学习、一起玩耍、一起运动……而现在我们即将踏入初中的校门,大家肯定都会有所不舍,那么就让我们来回顾一下吧！</p>
<h2>一、老师</h3>
<p>语文老师及班主任</p>
<p>1-3年级:陈丽剑老师(大陈老师)</p>
<p>4年级:陈玉娇老师(小陈老师)</p>
<p>5-6年级:徐珂老师(徐老师)</p>
<p>数学老师</p>
<p>1-2年级:(马老师)
<p>3-4年级:柳月春老师(柳老师)</p>
<p>5-6年级:李亚英老师(李老师)</p>
<p>英语老师</p>
<p>3-4年级:沈艳老师(沈老师)</p>
<p>5-6年级:康莉老师(康老师)</p>
<p>科学老师</p>
<p>3-4年级:王  老师(王老师)</p>
<p>5-6年级:俞佳成老师(俞老师)</p>
<p>体育老师</p>
<p>1-4年级:俞炼明老师(俞老师)</p>
<p>5-6年级:汪伟老师(汪老师)</p>
<p>美术老师</p>
<p>1-5年级:张胜望老师(张老师)</p>
<p>6年级:崔国梁老师(崔老师)</p>
<p>信息技术老师</p>
<p>4年级:俞佳成老师(俞老师)/干剑锋老师(干老师)</p>
<p>5年级:乌长义老师(乌老师)</p>
<p>6年级:赵卢老师(赵老师)</p>
<h3>二、班级</h4>
<p>1.学生名册</p>
<img src="/607/永远的607/学生名册.png" alt="图片无法显示" width="700" height="200" />
<p>2.委员架构</p>
<img src="/607/永远的607/组织架构.png" alt="图片无法显示" width="600" height="500" />
<p>3.获得荣誉</p>
<p>曾获得“吴兴区优秀班集体”荣誉称号及爱山小学教育集团仁皇校区“睛彩班级”、“书香班级”等荣誉称号,班内多名同学代表学校参加吴兴区中小学生田径运动会并夺得6年级跳高两个第一名。</p>
<img src="/607/永远的607/medal/6年级书声琅琅优胜班.JPG" width="400" height="300"  />
<img src="/607/永远的607/medal/6年级心理剧作品二等奖.JPG" width="400" height="300"  />
<img src="/607/永远的607/medal/6年级艺体节最佳合唱奖.JPG" width="400" height="300"  />
<h4>三、班级照片</h6>

<p style="color:red;"><b>1.让我们大家一起来上传607班的历史照片：</b></p>
<a href="/607/photo-new.html" target="self">感谢您的参与，进入照片上传页面</a></br>
<p>第一步输入“照片年级+上传学生姓名”；</p>
<p>第二步选择准备上传的照片文件，文件上传后会出现文件名（如文件很大，请耐心等待）；</p>
<p>第三步点击完成上传。</p>
<p>上传好的照片，整理后会在下方相应页面显示，方便大家后续观看</p>

6年级照片：<a href="/607/photo6.html" target="self">6年级照片</a></br>
5年级照片：<a href="/607/photo5.html" target="self">5年级照片</a></br>
4年级照片：<a href="/607/photo4.html" target="self">4年级照片</a></br>
3年级照片：<a href="/607/photo3.html" target="self">3年级照片</a></br>
2年级照片：<a href="/607/photo2.html" target="self">2年级照片</a></br>
1年级照片：<a href="/607/photo1.html" target="self">1年级照片</a></br>

<p style="color:red;"><b>2.607班毕业照正片：</b></p>
航拍照：<a href="/607/photo-hp.html" target="self">航拍照</a></br>
集体照：<a href="/607/photo-jt.html" target="self">集体照</a></br>
校服照：<a href="/607/photo-xf.html" target="self">校服照</a></br>
学院风照：<a href="/607/photo-xyf.html" target="self">学院风照</a></br>
运动服照：<a href="/607/photo-ydf.html" target="self">运动服照</a></br>
同桌照：<a href="/607/photo-tz.html" target="self">同桌照</a></br>

<p style="color:red;"><b>3.607班毕业照花絮（感谢徐易同学提供）：</b></p>
<a href="/607/photo.html" target="self">打包下载链接在最后</a></br>
<p style="color:red;"><b>4.607班毕业照视频（感谢蔡昊董同学提供）：</b></p>
<video  width="800" controls="controls"><source src="/607/永远的607/video/1.mp4" type="video/mp4">您的浏览器不支持HTML5视频。</video>
</br>
<video  width="800" controls="controls"><source src="/607/永远的607/video/毕业季.mp4" type="video/mp4">您的浏览器不支持HTML5视频。</video>
</br>
<video  width="800" controls="controls"><source src="/607/永远的607/video/2.mp4" type="video/mp4">您的浏览器不支持HTML5视频。</video>
</br>
</br>
<p align="center">网站访问量：<img src="gd2.php" style="position:relative; top: 4px;" /><a href="https://beian.miit.gov.cn" target="self">备案号：浙ICP备19017355号-1</a></p>

</body>
</html> 