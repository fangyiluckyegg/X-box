<?php require_once('../Connections/conn.php'); ?>
<?php
//initialize the session
if (!isset($_SESSION)) {
  session_start();
}

// ** Logout the current user. **
$logoutAction = $_SERVER['PHP_SELF']."?doLogout=true";
if ((isset($_SERVER['QUERY_STRING'])) && ($_SERVER['QUERY_STRING'] != "")){
  $logoutAction .="&". htmlentities($_SERVER['QUERY_STRING']);
}

if ((isset($_GET['doLogout'])) &&($_GET['doLogout']=="true")){
  //to fully log out a visitor we need to clear the session varialbles
  $_SESSION['MM_Username'] = NULL;
  $_SESSION['MM_UserGroup'] = NULL;
  $_SESSION['PrevUrl'] = NULL;
  unset($_SESSION['MM_Username']);
  unset($_SESSION['MM_UserGroup']);
  unset($_SESSION['PrevUrl']);
	
  $logoutGoTo = "../index.php";
  if ($logoutGoTo) {
    header("Location: $logoutGoTo");
    exit;
  }
}
?>
<?php
if (!function_exists("GetSQLValueString")) {
function GetSQLValueString($theValue, $theType, $theDefinedValue = "", $theNotDefinedValue = "") 
{
  if (PHP_VERSION < 6) {
    $theValue = get_magic_quotes_gpc() ? stripslashes($theValue) : $theValue;
  }

  $theValue = function_exists("mysql_real_escape_string") ? mysql_real_escape_string($theValue) : mysql_escape_string($theValue);

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

if ((isset($_GET['p_id'])) && ($_GET['p_id'] != "") && (isset($_POST['p_id']))) {
  $deleteSQL = sprintf("DELETE FROM work_pic WHERE p_id=%s",
                       GetSQLValueString($_GET['p_id'], "int"));

  mysql_select_db($database_conn, $conn);
  $Result1 = mysql_query($deleteSQL, $conn) or die(mysql_error());

  $deleteGoTo = "admin-work.php";
  if (isset($_SERVER['QUERY_STRING'])) {
    $deleteGoTo .= (strpos($deleteGoTo, '?')) ? "&" : "?";
    $deleteGoTo .= $_SERVER['QUERY_STRING'];
  }
  header(sprintf("Location: %s", $deleteGoTo));
}

$colname_rspic = "-1";
if (isset($_GET['p_id'])) {
  $colname_rspic = $_GET['p_id'];
}
mysql_select_db($database_conn, $conn);
$query_rspic = sprintf("SELECT * FROM work_pic inner join work_type on work_pic.t_id=work_type.t_id WHERE p_id = %s", GetSQLValueString($colname_rspic, "int"));
$rspic = mysql_query($query_rspic, $conn) or die(mysql_error());
$row_rspic = mysql_fetch_assoc($rspic);
$totalRows_rspic = mysql_num_rows($rspic);
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>删除作品页面</title>
<link href="style/style.css" rel="stylesheet" type="text/css">
</head>

<body>
<div id="box">
  <div id="top"><img src="images/top01.jpg" width="880" height="100" alt=""><img src="images/top02.jpg" width="920" height="65" alt=""></div>
  <div id="pic1">
    <div id="left">
      <div id="left-title">后台管理</div>
      <div id="type-list">
        <ul>
          <li><a href="admin-work.php">作品管理</a></li>
          <li><a href="add-work.php">添加作品</a></li>
          <li><a href="admin-type.php">分类管理</a></li>
          <li><a href="add-type.php">添加分类</a></li>
          <li><a href="<?php echo $logoutAction ?>">退出管理</a></li>
        </ul>
      </div>
    </div>
    <div id="right">
      <div id="title">
    删除作品</div>
      <div id="pic-work">
        <form id="form1" name="form1" method="post">
        <ul>
          <li>
            <label for="pic_name">标题名称：</label>
            <?php echo $row_rspic['p_name']; ?></li>
          <li>作品日期：<?php echo $row_rspic['p_date']; ?></li>
          <li>作品分类：<?php echo $row_rspic['t_name']; ?></li>
          <li>作品图片：<img src="upload/<?php echo $row_rspic['p_src']; ?>" width="195" height="122"/>
            <input name="p_id" type="hidden" id="p_id" value="<?php echo $row_rspic['p_id']; ?>">
          </li>
          <li>
            <input name="submit" type="submit" class="qrtj" id="submit" value="确认删除">
          </li>
        </ul>
        </form>
      </div>
    </div>
  </div>
</div>
</body>
</html>
<?php
mysql_free_result($rspic);
?>
