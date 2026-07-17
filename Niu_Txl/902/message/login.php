<?php
ob_start();
session_start();
require_once('Connections/conn.php');

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

$loginFormAction = $_SERVER['PHP_SELF'];
if (isset($_GET['accesscheck'])) {
  $_SESSION['PrevUrl'] = $_GET['accesscheck'];
}

if (isset($_POST['username'])) {
  $loginUsername = $_POST['username'];
  $password = $_POST['password'];
  $MM_redirectLoginSuccess = "index.php";
  $MM_redirectLoginFailed = "login.php";

  $LoginRS__query=sprintf("SELECT username, password FROM admin_user WHERE username=%s",
    GetSQLValueString($loginUsername, "text"));

  $LoginRS = mysql_query($LoginRS__query, $conn) or die(mysql_error());
  $loginFoundUser = mysql_num_rows($LoginRS);
  
  if ($loginFoundUser) {
    $row = mysql_fetch_array($LoginRS);
    if (password_verify($password, $row['password'])) {
      $_SESSION['MM_Username'] = $loginUsername;
      header("Location: " . $MM_redirectLoginSuccess);
      ob_end_flush();
      exit;
    }
  }
  // 用户名不存在或口令错误：统一跳回登录失败页
  header("Location: " . $MM_redirectLoginFailed);
  ob_end_flush();
  exit;
}
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>管理登录页面</title>
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
  <div id="main">
    <div id="msg">
      <div id="title">管理登录</div>
      <div id="login">
        <form ACTION="<?php echo $loginFormAction; ?>" method="POST">
          <ul>
            <li>管理员账号：<input name="username" type="text" required class="input01"></li>
            <li>管理员密码：<input name="password" type="password" required class="input01"></li>
            <li><input type="submit" value="登 录"></li>
          </ul>
        </form>
      </div>
    </div>
  </div>
</div>
</div>
</body>
</html>