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
if (!isset($_SESSION)) {
  session_start();
}
$MM_authorizedUsers = "";
$MM_donotCheckaccess = "true";

// *** Restrict Access To Page: Grant or deny access to this page
function isAuthorized($strUsers, $strGroups, $UserName, $UserGroup) { 
  // For security, start by assuming the visitor is NOT authorized. 
  $isValid = False; 

  // When a visitor has logged into this site, the Session variable MM_Username set equal to their username. 
  // Therefore, we know that a user is NOT logged in if that Session variable is blank. 
  if (!empty($UserName)) { 
    // Besides being logged in, you may restrict access to only certain users based on an ID established when they login. 
    // Parse the strings into arrays. 
    $arrUsers = Explode(",", $strUsers); 
    $arrGroups = Explode(",", $strGroups); 
    if (in_array($UserName, $arrUsers)) { 
      $isValid = true; 
    } 
    // Or, you may restrict access to only certain users based on their username. 
    if (in_array($UserGroup, $arrGroups)) { 
      $isValid = true; 
    } 
    if (($strUsers == "") && true) { 
      $isValid = true; 
    } 
  } 
  return $isValid; 
}

$MM_restrictGoTo = "login.php";
if (!((isset($_SESSION['MM_Username'])) && (isAuthorized("",$MM_authorizedUsers, $_SESSION['MM_Username'], $_SESSION['MM_UserGroup'])))) {   
  $MM_qsChar = "?";
  $MM_referrer = $_SERVER['PHP_SELF'];
  if (strpos($MM_restrictGoTo, "?")) $MM_qsChar = "&";
  if (isset($_SERVER['QUERY_STRING']) && strlen($_SERVER['QUERY_STRING']) > 0) 
  $MM_referrer .= "?" . $_SERVER['QUERY_STRING'];
  $MM_restrictGoTo = $MM_restrictGoTo. $MM_qsChar . "accesscheck=" . urlencode($MM_referrer);
  header("Location: ". $MM_restrictGoTo); 
  exit;
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

$currentPage = $_SERVER["PHP_SELF"];

$maxRows_rspic = 9;
$pageNum_rspic = 0;
if (isset($_GET['pageNum_rspic'])) {
  $pageNum_rspic = $_GET['pageNum_rspic'];
}
$startRow_rspic = $pageNum_rspic * $maxRows_rspic;

mysql_select_db($database_conn, $conn);
$query_rspic = "SELECT * FROM work_pic inner join work_type on work_pic.t_id=work_type.t_id ORDER BY p_id DESC";
$query_limit_rspic = sprintf("%s LIMIT %d, %d", $query_rspic, $startRow_rspic, $maxRows_rspic);
$rspic = mysql_query($query_limit_rspic, $conn) or die(mysql_error());
$row_rspic = mysql_fetch_assoc($rspic);

if (isset($_GET['totalRows_rspic'])) {
  $totalRows_rspic = $_GET['totalRows_rspic'];
} else {
  $all_rspic = mysql_query($query_rspic);
  $totalRows_rspic = mysql_num_rows($all_rspic);
}
$totalPages_rspic = ceil($totalRows_rspic/$maxRows_rspic)-1;

mysql_select_db($database_conn, $conn);
$query_rstype = "SELECT * FROM work_type";
$rstype = mysql_query($query_rstype, $conn) or die(mysql_error());
$row_rstype = mysql_fetch_assoc($rstype);
$totalRows_rstype = mysql_num_rows($rstype);

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
<title>作品管理页面</title>
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
      <div id="title">作品管理</div>
      <div id="pic-work">
        <?php if ($totalRows_rspic > 0) { // Show if recordset not empty ?>
          <?php do { ?>
            <div class="work1"><img src="upload/<?php echo $row_rspic['p_src']; ?>" alt="" width="195" height="122"><br>
              <span class="font02"><?php echo $row_rspic['p_name']; ?></span><br>
              [<?php echo $row_rspic['t_name']; ?>]　<?php echo $row_rspic['p_date']; ?><br>
            <div class="manage">【<a href="updata-work.php?p_id=<?php echo $row_rspic['p_id']; ?>">修改</a>】 【<a href="del-work.php?p_id=<?php echo $row_rspic['p_id']; ?>">删除</a>】</div></div>
            <?php } while ($row_rspic = mysql_fetch_assoc($rspic)); ?>
          <?php } // Show if recordset not empty ?>
      </div>
      <div id="bar"><a href="<?php printf("%s?pageNum_rspic=%d%s", $currentPage, 0, $queryString_rspic); ?>">第一页</a>　<a href="<?php printf("%s?pageNum_rspic=%d%s", $currentPage, max(0, $pageNum_rspic - 1), $queryString_rspic); ?>">上一页</a>　<a href="<?php printf("%s?pageNum_rspic=%d%s", $currentPage, min($totalPages_rspic, $pageNum_rspic + 1), $queryString_rspic); ?>">下一页</a>　<a href="<?php printf("%s?pageNum_rspic=%d%s", $currentPage, $totalPages_rspic, $queryString_rspic); ?>">最后一页</a></div>
    </div>
  </div>
</div>
</body>
</html>
<?php
mysql_free_result($rspic);

mysql_free_result($rstype);
?>
