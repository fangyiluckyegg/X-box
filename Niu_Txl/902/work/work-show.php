<?php
ob_start();
session_start();
require_once('Connections/conn.php');
?>
<?php
if (!function_exists("GetSQLValueString")) {
function GetSQLValueString($theValue, $theType, $theDefinedValue = "", $theNotDefinedValue = "") 
{
  global $conn;

  if (PHP_VERSION < 6) {
    $theValue = get_magic_quotes_gpc() ? stripslashes($theValue) : $theValue;
  }

  // 🔥 直接用 mysqli，永远不报错
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

$colname_rspic = "-1";
if (isset($_GET['p_id'])) {
  $colname_rspic = $_GET['p_id'];
}

// 🔥 移除报错的 mysql_select_db，改用 mysqli
$query_rspic = sprintf("SELECT * FROM work_pic WHERE p_id = %s", GetSQLValueString($colname_rspic, "int"));
$rspic = mysqli_query($conn, $query_rspic) or die(mysqli_error($conn));
$row_rspic = mysqli_fetch_assoc($rspic);
$totalRows_rspic = mysqli_num_rows($rspic);
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>查看作品页面</title>
<link href="style/style.css" rel="stylesheet" type="text/css">
</head>

<body>
<div id="box">
  <div id="top"><img src="images/top01.jpg" width="880" height="100" alt=""><img src="images/top02.jpg" width="920" height="65" alt=""></div>
  <div id="pic1">
    <div id="work-box">
      <div id="title"><span class="font01">作品日期：<?php echo $row_rspic['p_date']; ?></span><?php echo $row_rspic['p_name']; ?></div>
      <div id="work-show"><img src="admin/upload/<?php echo $row_rspic['p_src']; ?>"></div>
    </div>
  </div>
</div>
</body>
</html>
<?php
mysqli_free_result($rspic);
?>