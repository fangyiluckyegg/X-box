<?php session_start(); ?><?php require_once('Connections/conn.php'); ?>
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

mysqli_select_db($conn, $database_conn);
$query_rstype = "SELECT * FROM work_type";
$rstype = mysqli_query($conn, $query_rstype) or die(mysqli_error($conn));
$row_rstype = mysqli_fetch_assoc($rstype);
$totalRows_rstype = mysqli_num_rows($rstype);
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>班级风采展示管理系统首页</title>
<link href="style/style.css" rel="stylesheet" type="text/css">
</head>

<body>
<div id="box">
  <div id="top"><img src="images/top01.jpg" width="880" height="100" alt=""><img src="images/top02.jpg" width="920" height="65" alt=""></div>
  <div id="pic1">
    <?php if ($totalRows_rstype > 0) { ?>
      <?php do { ?>
        <div id="work-box">
          <div id="title"><span class="font01"><a href="work-type.php?t_id=<?php echo $row_rstype['t_id']; ?>">更多...</a></span><?php echo $row_rstype['t_name']; ?>作品</div>
          <div id="pic-work"><?php include("list.php"); ?></div>
        </div>
        <?php } while ($row_rstype = mysqli_fetch_assoc($rstype)); ?>
      <?php } ?>
  </div>
</div>
</body>
</html>
<?php
mysqli_free_result($rstype);
?>