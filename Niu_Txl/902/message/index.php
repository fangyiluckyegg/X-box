<?php session_start(); ?><?php require_once('Connections/conn.php'); ?><?php //这句话使用了require_once函数调用了Connections文件夹下conn.php文件，连接数据库 ?>
<?php
if (!function_exists("GetSQLValueString")) {//如果!function_exists函数得到了"GetSQLValueString"函数已定义的结果，就执行下面这行语句
function GetSQLValueString($theValue, $theType, $theDefinedValue = "", $theNotDefinedValue = "") 
{
  global $conn;
  
  if (PHP_VERSION < 6) {//如果PHP版本小于6，那么将变量$theValue替换成get_magic_quotes_gpc()这个函数
    $theValue = get_magic_quotes_gpc() ? stripslashes($theValue) : $theValue;
  }

  // 这里彻底修复老函数
  $theValue = mysqli_real_escape_string($conn, $theValue);

  switch ($theType) {//将变量$theType分别与"text","long","int","double","date"和"defined"作比较
    case "text":
      $theValue = ($theValue != "") ? "'" . $theValue . "'" : "NULL";
      break;    
    case "long":
    case "int":
      $theValue = ($theValue != "") ? intval($theValue) : "NULL";
      break;
    case "double":
      $theValue = ($theValue != "") ? doubleval($theValue) : "NULL";
      break;
    case "date":
      $theValue = ($theValue != "") ? "'" . $theValue . "'" : "NULL";
      break;
    case "defined":
      $theValue = ($theValue != "") ? $theDefinedValue : $theNotDefinedValue;
      break;
  }
  return $theValue;
}
}

$currentPage = $_SERVER["PHP_SELF"];

$maxRows_rsview = 5;
$pageNum_rsview = 0;
if (isset($_GET['pageNum_rsview'])) {
  $pageNum_rsview = $_GET['pageNum_rsview'];
}
$startRow_rsview = $pageNum_rsview * $maxRows_rsview;

mysqli_select_db($conn, $database_conn);
$query_rsview = "SELECT * FROM post ORDER BY P_ID DESC";
$query_limit_rsview = sprintf("%s LIMIT %d, %d", $query_rsview, $startRow_rsview, $maxRows_rsview);
$rsview = mysqli_query($conn, $query_limit_rsview) or die(mysqli_error($conn));
$row_rsview = mysqli_fetch_assoc($rsview);

if (isset($_GET['totalRows_rsview'])) {
  $totalRows_rsview = $_GET['totalRows_rsview'];
} else {
  $all_rsview = mysqli_query($conn,$query_rsview);
  $totalRows_rsview = mysqli_num_rows($all_rsview);
}
$totalPages_rsview = ceil($totalRows_rsview/$maxRows_rsview)-1;

$queryString_rsview = "";
if (!empty($_SERVER['QUERY_STRING'])) {
  $params = explode("&", $_SERVER['QUERY_STRING']);
  $newParams = array();
  foreach ($params as $param) {
    if (stristr($param, "pageNum_rsview") == false && stristr($param, "totalRows_rsview") == false) {
      array_push($newParams, $param);
    }
  }
  if (count($newParams) != 0) {
    $queryString_rsview = "&" . htmlentities(implode("&", $newParams));
  }
}
$queryString_rsview = sprintf("&totalRows_rsview=%d%s", $totalRows_rsview, $queryString_rsview);
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>留言板首页</title>
<link href="style/style.css" rel="stylesheet" type="text/css">
</head>

<body>
<div id="bg">
  <div id="logo"><img src="images/logo1.png" width="125" height="125" alt="" /></div>
  <div id="box">
  <div id="menu">
    <ul>
      <li><a href="login.php">留言管理</a></li>
      <li><a href="add-msg.php">我要留言</a></li>
      <li><a href="index.php">留言板</a></li>
    </ul>
  </div>
  <div id="jishu">共有<span class="red01"> <?php echo $totalRows_rsview ?> </span>条留言，目前显示第<span class="red01"> <?php echo ($startRow_rsview + 1) ?> </span>条至第<span class="red01"> <?php echo min($startRow_rsview + $maxRows_rsview, $totalRows_rsview) ?> </span>条</div>
  <div id="main">
    <?php if ($totalRows_rsview > 0) { ?>
      <?php do { ?>
        <div id="msg">
          <div id="msg-left"> <img src="<?php echo htmlspecialchars($row_rsview['P_Pic'], ENT_QUOTES, 'UTF-8'); ?>" width="100" height="100" alt="图片无法显示" onerror="this.src='images/photo.png'"/><br>
            <?php echo htmlspecialchars($row_rsview['P_Name'], ENT_QUOTES, 'UTF-8'); ?><br>
            <?php echo htmlspecialchars($row_rsview['P_Mail'], ENT_QUOTES, 'UTF-8'); ?></div>
          <div id="msg-right">
            <div id="msg1"><?php if(isset($_SESSION['MM_Username'])) { ?><span class="font01">【<a href="del-msg.php?P_ID=<?php echo $row_rsview['P_ID'];?>">删除</a>】</span><?php } ?>
            <?php echo $row_rsview['P_Date']; ?></div>
            <div id="msg-text">
			<?php 
			  // SEC11 修复：用户留言内容用 strip_tags 白名单过滤后原样渲染，既保留富文本格式，又防 script/style/iframe 等存储型 XSS
			  $allowedTags = '<p><span><em><strong><b><i><u><br><a><img><ul><ol><li><h1><h2><h3><h4><h5><h6><blockquote><code><pre>';
			  if($row_rsview['P_Private'] == 1)
			    if(isset($_SESSION['MM_Username']))
			      echo strip_tags($row_rsview['P_Content'], $allowedTags); 
                else
				   echo "<b>该留言内容仅管理员可见...</b>";
		      else
			    echo strip_tags($row_rsview['P_Content'], $allowedTags);
            ?>    
            </div>
            <div id="msg2"><?php include("reply.php"); ?></div>
            <div id="msg3"><a href="reply-msg.php?P_ID=<?php echo $row_rsview['P_ID'];?>">回复</a></div>
          </div>
        </div>
        <?php } while ($row_rsview = mysqli_fetch_assoc($rsview)); ?>
      <?php } ?>
    <?php if ($totalRows_rsview == 0) { ?>
      <div id="no-msg">目前还没有留言</div>
      <?php } ?>
<div id="bar"><a href="<?php printf("%s?pageNum_rsview=%d%s", $currentPage, 0, $queryString_rsview); ?>">第一页</a>　<a href="<?php printf("%s?pageNum_rsview=%d%s", $currentPage, max(0, $pageNum_rsview - 1), $queryString_rsview); ?>">上一页</a>　<a href="<?php printf("%s?pageNum_rsview=%d%s", $currentPage, min($totalPages_rsview, $pageNum_rsview + 1), $queryString_rsview); ?>">下一页</a>　<a href="<?php printf("%s?pageNum_rsview=%d%s", $currentPage, $totalPages_rsview, $queryString_rsview); ?>">最后一页</a></div>
  </div>
</div>
</div>
</body>
</html>
<?php
mysqli_free_result($rsview);
?>