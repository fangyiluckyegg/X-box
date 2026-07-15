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

$editFormAction = $_SERVER['PHP_SELF'];
if (isset($_SERVER['QUERY_STRING'])) {
  $editFormAction .= "?" . htmlentities($_SERVER['QUERY_STRING']);
}

if ((isset($_POST["MM_insert"])) && ($_POST["MM_insert"] == "form1")) {
  $insertSQL = sprintf("INSERT INTO work_pic (t_id, p_src, p_name, p_date) VALUES (%s, %s, %s, %s)",
                       GetSQLValueString($_POST['select'], "int"),
                       GetSQLValueString($_POST['rePic'], "text"),
                       GetSQLValueString($_POST['pic_name'], "text"),
                       GetSQLValueString($_POST['pic_date'], "date"));

  mysql_select_db($database_conn, $conn);
  $Result1 = mysql_query($insertSQL, $conn) or die(mysql_error());

  $insertGoTo = "admin-work.php";
  if (isset($_SERVER['QUERY_STRING'])) {
    $insertGoTo .= (strpos($insertGoTo, '?')) ? "&" : "?";
    $insertGoTo .= $_SERVER['QUERY_STRING'];
  }
  header(sprintf("Location: %s", $insertGoTo));
}

mysql_select_db($database_conn, $conn);
$query_rstype = "SELECT * FROM work_type";
$rstype = mysql_query($query_rstype, $conn) or die(mysql_error());
$row_rstype = mysql_fetch_assoc($rstype);
$totalRows_rstype = mysql_num_rows($rstype);
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>添加作品页面</title>
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
    添加作品</div>
      <div id="pic-work">
        <form action="<?php echo $editFormAction; ?>" id="form1" name="form1" method="POST">
        <ul>
          <li>标题名称：
            <input name="pic_name" type="text" required class="input01" id="pic_name" placeholder="请输入作品名称">
          </li>
          <li>作品日期：
            <input name="pic_date" type="text" required class="input01" id="pic_date" placeholder="请输入作品日期">
          （YYYY-MM-DD）</li>
          <li>作品分类：
            <select name="select" class="input02" id="select">
              <?php
do {  
?>
              <option value="<?php echo $row_rstype['t_id']?>"<?php if (!(strcmp($row_rstype['t_id'], $row_rstype['t_id']))) {echo "selected=\"selected\"";} ?>><?php echo $row_rstype['t_name']?></option>
              <?php
} while ($row_rstype = mysql_fetch_assoc($rstype));
  $rows = mysql_num_rows($rstype);
  if($rows > 0) {
      mysql_data_seek($rstype, 0);
	  $row_rstype = mysql_fetch_assoc($rstype);
  }
?>
            </select>
          </li>
          <li>上传图片：
            <input type="button" name="button1" id="button1" value="上传图片" onclick="window.open('fupload.php?useForm=form1&amp;prevImg=showImg&amp;upUrl=upload&amp;reItem=rePic','fileUpload','width=400,height=180')">
            <input type="hidden" name="rePic" id="rePic" value=" ">
            <img src="images/pic.jpg" alt="这是显示上传预览图片的位置" width="195" height="122" id="showImg" onclick='javascript:alert(&quot;这是显示上传预览图片的位置&quot;);'/></li>
          <li>
            <input name="submit" type="submit" class="qrtj" id="submit" value="确认添加">
          </li>
        </ul>
        <input type="hidden" name="MM_insert" value="form1">
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
