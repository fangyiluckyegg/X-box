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

$editFormAction = $_SERVER['PHP_SELF'];
if (isset($_SERVER['QUERY_STRING'])) {
  $editFormAction .= "?" . htmlentities($_SERVER['QUERY_STRING']);
}

if ((isset($_POST["MM_update"])) && ($_POST["MM_update"] == "form1")) {
  $updateSQL = sprintf("UPDATE work_type SET t_name=%s WHERE t_id=%s",
                       GetSQLValueString($_POST['type_name'], "text"),
                       GetSQLValueString($_POST['t_id'], "int"));

  mysql_select_db($database_conn, $conn);
  $Result1 = mysql_query($updateSQL, $conn) or die(mysql_error());

  $updateGoTo = "admin-type.php";
  if (isset($_SERVER['QUERY_STRING'])) {
    $updateGoTo .= (strpos($updateGoTo, '?')) ? "&" : "?";
    $updateGoTo .= $_SERVER['QUERY_STRING'];
  }
  header(sprintf("Location: %s", $updateGoTo));
}

$colname_rstype = "-1";
if (isset($_GET['t_id'])) {
  $colname_rstype = $_GET['t_id'];
}
mysql_select_db($database_conn, $conn);
$query_rstype = sprintf("SELECT * FROM work_type WHERE t_id = %s", GetSQLValueString($colname_rstype, "int"));
$rstype = mysql_query($query_rstype, $conn) or die(mysql_error());
$row_rstype = mysql_fetch_assoc($rstype);
$totalRows_rstype = mysql_num_rows($rstype);
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>修改作品分类页面</title>
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
    修改作品分类</div>
      <div id="pic-work">
        <form action="<?php echo $editFormAction; ?>" id="form1" name="form1" method="POST">
        <ul>
          <li>作品分类名称：
            <input name="type_name" type="text" required class="input01" id="type_name" placeholder="请输入分类名称" value="<?php echo $row_rstype['t_name']; ?>">
            <input name="t_id" type="hidden" id="t_id" value="<?php echo $row_rstype['t_id']; ?>">
          </li>
          <li>
            <input name="submit" type="submit" class="qrtj" id="submit" value="确认修改">
          </li>
        </ul>
        <input type="hidden" name="MM_update" value="form1">
        </form>
      </div>
    </div>
  </div>
</div>
</body>
</html>
<?php
mysql_free_result($rstype);
?>
