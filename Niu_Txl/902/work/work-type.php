<?php require_once('Connections/conn.php'); ?>
<?php
if (!function_exists("GetSQLValueString")) {
function GetSQLValueString($theValue, $theType, $theDefinedValue = "", $theNotDefinedValue = "") 
{
  global $conn;

  if (PHP_VERSION < 6) {
    $theValue = get_magic_quotes_gpc() ? stripslashes($theValue) : $theValue;
  }

  $theValue = mysqli_real_escape_string($conn, $theValue);

  switch ($theType) {
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

$query_rstype = "SELECT * FROM work_type";
$rstype = mysqli_query($conn, $query_rstype) or die(mysqli_error($conn));
$row_rstype = mysqli_fetch_assoc($rstype);
$totalRows_rstype = mysqli_num_rows($rstype);

$colname_rstype2 = "-1";
if (isset($_GET['t_id'])) {
  $colname_rstype2 = $_GET['t_id'];
}
$query_rstype2 = sprintf("SELECT t_name FROM work_type WHERE t_id = %s", GetSQLValueString($colname_rstype2, "int"));
$rstype2 = mysqli_query($conn, $query_rstype2) or die(mysqli_error($conn));
$row_rstype2 = mysqli_fetch_assoc($rstype2);
$totalRows_rstype2 = mysqli_num_rows($rstype2);

$maxRows_rspic = 9;
$pageNum_rspic = 0;
if (isset($_GET['pageNum_rspic'])) {
  $pageNum_rspic = $_GET['pageNum_rspic'];
}
$startRow_rspic = $pageNum_rspic * $maxRows_rspic;

$colname_rspic = "-1";
if (isset($_GET['t_id'])) {
  $colname_rspic = $_GET['t_id'];
}
$query_rspic = sprintf("SELECT * FROM work_pic WHERE t_id = %s ORDER BY p_id DESC", GetSQLValueString($colname_rspic, "int"));
$query_limit_rspic = sprintf("%s LIMIT %d, %d", $query_rspic, $startRow_rspic, $maxRows_rspic);
$rspic = mysqli_query($conn, $query_limit_rspic) or die(mysqli_error($conn));
$row_rspic = mysqli_fetch_assoc($rspic);

if (isset($_GET['totalRows_rspic'])) {
  $totalRows_rspic = $_GET['totalRows_rspic'];
} else {
  $all_rspic = mysqli_query($conn, $query_rspic);
  $totalRows_rspic = mysqli_num_rows($all_rspic);
}
$totalPages_rspic = ceil($totalRows_rspic/$maxRows_rspic)-1;

$queryString_rspic = "";
if (!empty($_SERVER['QUERY_STRING'])) {
  $params = explode("&", $_SERVER['QUERY_STRING']);
  $newParams = array();
  foreach ($params as $param) {
    if (stristr($param, "pageNum_rspic") == false && 
        stristr($param, "totalRows_rspic") == false) {
      array_push($newParams, $param);
    }
  }
  if (count($newParams) != 0) {
    $queryString_rspic = "&" . htmlentities(implode("&", $newParams));
  }
}
$queryString_rspic = sprintf("&totalRows_rspic=%d%s", $totalRows_rspic, $queryString_rspic);
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>作品分类列表页面</title>
<link href="style/style.css" rel="stylesheet" type="text/css">
</head>

<body>
<div id="box">
  <div id="top"><img src="images/top01.jpg" width="880" height="100" alt=""><img src="images/top02.jpg" width="920" height="65" alt=""></div>
  <div id="pic1">
    <div id="left">
      <div id="left-title">作品分类</div>
      <div id="type-list">
        <ul>
          <li><a href="work-all.php">全部作品</a></li>
          <?php if ($totalRows_rstype > 0) { ?>
          <?php do { ?>
            <li><a href="work-type.php?t_id=<?php echo $row_rstype['t_id']; ?>"><?php echo $row_rstype['t_name']; ?></a></li>
          <?php } while ($row_rstype = mysqli_fetch_assoc($rstype)); ?>
          <?php } ?>
        </ul>
      </div>
    </div>
    <div id="right">
      <div id="title"><span class="font01">共有 <?php echo $totalRows_rspic ?> 个作品，当前显示第 <?php echo ($startRow_rspic + 1) ?> 个至第 <?php echo min($startRow_rspic + $maxRows_rspic, $totalRows_rspic) ?> 个</span><?php echo $row_rstype2['t_name']; ?>作品</div>
      <div id="pic-work">
        <?php if ($totalRows_rspic > 0) { ?>
          <?php do { ?>
            <div class="work1">
              <a href="work-show.php?p_id=<?php echo $row_rspic['p_id']; ?>" target="_blank">
                <img src="admin/upload/<?php echo $row_rspic['p_src']; ?>" alt="" width="195" height="122">
              </a><br>
              <span class="font02"><?php echo $row_rspic['p_name']; ?></span><br>
              <?php echo $row_rspic['p_date']; ?>
            </div>
          <?php } while ($row_rspic = mysqli_fetch_assoc($rspic)); ?>
        <?php } ?>
      </div>
      <div id="bar">
        <a href="<?php printf("%s?pageNum_rspic=%d%s", $currentPage, 0, $queryString_rspic); ?>">第一页</a>　
        <a href="<?php printf("%s?pageNum_rspic=%d%s", $currentPage, max(0, $pageNum_rspic - 1), $queryString_rspic); ?>">上一页</a>　
        <a href="<?php printf("%s?pageNum_rspic=%d%s", $currentPage, min($totalPages_rspic, $pageNum_rspic + 1), $queryString_rspic); ?>">下一页</a>　
        <a href="<?php printf("%s?pageNum_rspic=%d%s", $currentPage, $totalPages_rspic, $queryString_rspic); ?>">最后一页</a>
      </div>
    </div>
  </div>
</div>
</body>
</html>
<?php
mysqli_free_result($rstype);
mysqli_free_result($rstype2);
mysqli_free_result($rspic);
?>