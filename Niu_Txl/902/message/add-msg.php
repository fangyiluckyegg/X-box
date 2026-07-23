<?php session_start(); ?>
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

$editFormAction = $_SERVER['PHP_SELF'];
if (isset($_SERVER['QUERY_STRING'])) {
  $editFormAction .= "?" . htmlentities($_SERVER['QUERY_STRING']);
}

if ((isset($_POST["MM_insert"])) && ($_POST["MM_insert"] == "form1")) {
  $insertSQL = sprintf("INSERT INTO post (P_Name, P_Pic, P_Mail, P_Date, P_Content, P_Private) VALUES (%s, %s, %s, %s, %s, %s)",
                       GetSQLValueString($_POST['uname'], "text"),
                       GetSQLValueString($_POST['select1'], "text"),
                       GetSQLValueString($_POST['email'], "text"),
                       GetSQLValueString($_POST['P_Date'], "date"),
                       GetSQLValueString($_POST['textarea'], "text"),
                       GetSQLValueString($_POST['Radio1'], "int"));

  $Result1 = mysqli_query($conn, $insertSQL) or die(mysqli_error($conn));

  $insertGoTo = "index.php";
  if (isset($_SERVER['QUERY_STRING'])) {
    $insertGoTo .= (strpos($insertGoTo, '?')) ? "&" : "?";
    $insertGoTo .= $_SERVER['QUERY_STRING'];
  }
  header(sprintf("Location: %s", $insertGoTo));
}
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>发表留言页面</title>
<link href="style/style.css" rel="stylesheet" type="text/css">
<script type="text/javascript">
function show() {
	document.getElementById("headimg").src = document.getElementById("select1").value;
}
</script>
<script src="tinymce/js/tinymce/tinymce.min.js"></script>
<script src="tinymce/js/tinymce/langs/zh_CN.js"></script>
<script>tinymce.init({ selector:'textarea' });</script>
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
      <div id="title">发表留言</div>
      <div id="add">
        <form action="<?php echo $editFormAction; ?>" id="form1" name="form1" method="POST">
          <ul>
            <li>用户昵称：
              <input name="uname" type="text" required class="input01" id="uname" placeholder="清输入用户昵称，格式为昵称+(姓名+学号)">
            </li>
            <li>电子邮箱：
              <input name="email" type="email" required class="input01" id="email" placeholder="请输入有效的E-Mail地址，如abc123@163.com">
              <input name="P_Date" type="hidden" id="P_Date" value="<?php date_default_timezone_set('Asia/Shanghai'); echo date("Y:m:d H:i:s"); ?>">
            </li>
            <li>用户头像：
              <select name="select1" class="input02" id="select1" onChange="show()">
			          <option value="images/photo.png">管理员头像</option>
                <option value="images/photo1.png">用户头像1</option>
                <option value="images/photo2.png">用户头像2</option>
                <option value="images/photo3.png">用户头像3</option>
                <option value="images/photo4.png">用户头像4</option>
                <option value="images/photo5.png">用户头像5</option>
                <option value="images/photo6.png">用户头像6</option>
                <option value="images/photo7.png">用户头像7</option>
                <option value="images/photo8.png">用户头像8</option>
                <option value="images/photo9.png">用户头像9</option>
              </select>
              <img src="images/photo.png" alt="" width="100" height="100" id="headimg"/></li>
            <li>是否仅管理员可见：
              <input type="radio" name="Radio1" value="1" id="Radio1"> 是
              <input name="Radio1" type="radio" id="Radio1" value="0" checked> 否</li>
            <li>用户留言：
              <textarea name="textarea" class="input03" id="textarea"></textarea>
            </li>
            <li>
              <input type="submit" name="qr" id="qr" value="确认留言">
            </li>
          </ul>
          <input type="hidden" name="MM_insert" value="form1">
        </form>
      </div>
    </div>
  </div>
</div>
</div>
</body>
</html>