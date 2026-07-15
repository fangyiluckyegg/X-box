<?php
ob_start();
session_start();
require_once('Connections/conn.php');
?>
<?php //这句话使用了require_once函数调用了Connections文件夹下的conn.php文件，连接数据库 ?>
<?php
if (!function_exists("GetSQLValueString")) {//如果!function_exists函数得到了"GetSQLValueString"的参数，就执行下面这行语句
function GetSQLValueString($theValue, $theType, $theDefinedValue = "", $theNotDefinedValue = "") 
{
  global $conn;

  if (PHP_VERSION < 6) {//如果PHP版本小于6，那么将变量$theValue替换成get_magic_quotes_gpc()这个函数
    $theValue = get_magic_quotes_gpc() ? stripslashes($theValue) : $theValue;
  }

  // 🔥 修复：直接使用 mysqli，不再依赖老函数
  $theValue = mysqli_real_escape_string($conn, $theValue);

  switch ($theType) {//将变量$theType分别与"text","long","int","double","date"和"defined"作比较，并根据比较结果来决定执行哪一段程序段
    case "text"://如果变量$theType="text"时，就把变量$theValue替换成下面这行语句
      $theValue = ($theValue != "") ? "'" . $theValue . "'" : "NULL";
      break;    
    case "long":
    case "int"://如果变量$theType="int"时，就把变量$theValue替换成下面这行语句
      $theValue = ($theValue != "") ? intval($theValue) : "NULL";
      break;
    case "double"://如果变量$theType="double"时，就把变量$theValue替换成下面这行语句
      $theValue = ($theValue != "") ? doubleval($theValue) : "NULL";
      break;
    case "date"://如果变量$theType="date"时，就把变量$theValue替换成下面这行语句
      $theValue = ($theValue != "") ? "'" . $theValue . "'" : "NULL";
      break;
    case "defined"://如果变量$theType="defined"时，就把变量$theValue替换成下面这行语句
      $theValue = ($theValue != "") ? $theDefinedValue : $theNotDefinedValue;
      break;
  }
  return $theValue;//最后再返回变量$theValue
}
}

$editFormAction = $_SERVER['PHP_SELF'];//获取文件名
if (isset($_SERVER['QUERY_STRING'])) {//设置打开网页链接
  $editFormAction .= "?" . htmlentities($_SERVER['QUERY_STRING']);
}

if ((isset($_POST["MM_insert"])) && ($_POST["MM_insert"] == "form1")) {//设置提交回复链接
  $insertSQL = sprintf("INSERT INTO reply (R_Post, R_Name, R_Pic, R_Mail, R_Date, R_Content) VALUES (%s, %s, %s, %s, %s, %s)",//当调用这个文件时将这些变量插入reply表中
                       GetSQLValueString($_POST['R_Post'], "int"),//调用'R_Post'变量，为整型
                       GetSQLValueString($_POST['uname'], "text"),//调用'uname'变量，为文本类型
                       GetSQLValueString($_POST['select1'], "text"),//调用'select1'变量，为文本类型
                       GetSQLValueString($_POST['email'], "text"),//调用'email'变量，为文本类型
                       GetSQLValueString($_POST['R_Date'], "date"),//调用'R_Date'变量，为日期类型
                       GetSQLValueString($_POST['textarea'], "text"));//调用'textarea'变量，为文本类型

  // 🔥 修复：使用 mysqli
  $Result1 = mysqli_query($conn, $insertSQL) or die(mysqli_error($conn));//启动插入语句

  $insertGoTo = "index.php";//链接主页面
  if (isset($_SERVER['QUERY_STRING'])) {//设置打开主页面链接
    $insertGoTo .= (strpos($insertGoTo, '?')) ? "&" : "?";
    $insertGoTo .= $_SERVER['QUERY_STRING'];
  }
  header(sprintf("Location: %s", $insertGoTo));
  ob_end_flush();
  exit;
}
?>
<!doctype html><?php //声明此文件类型为HTML ?>
<html>
<head>
<meta charset="utf-8"><?php //使此文件使用utf-8编码 ?>
<title>发表留言页面</title><?php //设置标题 ?>
<link href="style/style.css" rel="stylesheet" type="text/css"><?php //连接style.css并设置各项属性 ?>
<script type="text/javascript"><?php //声明文本类型为Javascript ?>
function show() {
	document.getElementById("headimg").src = document.getElementById("select1").value; 
	} 
</script><?php //在上面这一段代码里定义名称为show()的函数，在该函数中将页面中ID名称为select1的元素的value属性值赋予页面中ID名称为heading的元素的src属性。 ?>
<script src="tinymce/js/tinymce/tinymce.min.js"></script><?php //载入tinymce文本编辑器 ?>
<script src="tinymce/js/tinymce/langs/zh_CN.js"></script><?php //载入tinymce文本编辑器中文语言包 ?>
<script>tinymce.init({ selector:'textarea' });</script><?php //这行代码使此页面中的所有文本区域都将使用TinyMCE编辑器。 ?>
</head>

<body>
<div id="bg"><?php //设置网站背景 ?>
  <div id="logo"><img src="images/logo1.png" width="125" height="125" alt="" /></div><?php //设置logo图片来源 ?>
<div id="box">
  <div id="menu"><?php //设置菜单 ?>
    <ul>
      <li><a href="login.php">留言管理</a></li><?php //连接留言管理页面 ?>
      <li><a href="add-msg.php">我要留言</a></li><?php //连接我要留言页面 ?>
      <li><a href="index.php">留言板</a></li><?php //连接留言板页面 ?>
    </ul>
  </div>
  <div id="main"><?php //设置主页面 ?>
    <div id="msg">
      <div id="title">回复留言</div><?php //设置标题 ?>
      <div id="add">
        <form action="<?php echo $editFormAction; ?>" id="form1" name="form1" method="POST">
          <ul>
            <li>用户昵称：
              <input name="uname" type="text" required class="input01" id="uname" placeholder="清输入用户昵称，格式为昵称+(姓名+学号)"><?php //设置用户昵称输入框 ?>
              <input type="hidden" name="R_Date" id="R_Date" value="<?php date_default_timezone_set('Asia/Shanghai'); echo date("Y:m:d H:i:s"); ?>"><?php //设置时区与日期显示格式 ?>
              <input name="R_Post" type="hidden" id="R_Post" value="<?php echo htmlspecialchars($_GET['P_ID'], ENT_QUOTES, 'UTF-8'); ?>">
            </li>
            <li>电子邮箱：
              <input name="email" type="email" required class="input01" id="email" placeholder="请输入有效的E-Mail地址，如abc123@163.com"><?php //设置电子邮箱输入框 ?>
            </li>
            <li>用户头像：
              <select name="select1" class="input02" id="select1" onChange="show()"><?php //设置用户头像下拉框 ?>
                <option value="images/photo1.png">用户头像1</option><?php //导入用户头像 ?>
                <option value="images/photo2.png">用户头像2</option>
                <option value="images/photo3.png">用户头像3</option>
                <option value="images/photo4.png">用户头像4</option>
                <option value="images/photo5.png">用户头像5</option>
                <option value="images/photo6.png">用户头像6</option>
                <option value="images/photo7.png">用户头像7</option>
                <option value="images/photo8.png">用户头像8</option>
                <option value="images/photo9.png">用户头像9</option>
				        <option value="images/photo10.png">用户头像10</option>
				        <option value="images/photo11.png">用户头像11</option>
				        <option value="images/photo12.png">用户头像12</option>
				        <option value="images/photo13.png">用户头像13</option>
				        <option value="images/photo14.png">用户头像14</option>
				        <option value="images/photo15.png">用户头像15</option>
                <option value="images/photo16.png">用户头像16</option>
                <option value="images/photo17.png">用户头像17</option>
                <option value="images/photo18.png">用户头像18</option>
                <option value="images/photo19.png">用户头像19</option>
                <option value="images/photo20.png">用户头像20</option>
              </select>
              <img src="images/photo.png" alt="" width="100" height="100" id="headimg"/></li><?php //设置页面logo ?>
            <li>用户留言：
              <textarea name="textarea" class="input03" id="textarea"></textarea>
            </li>
            <li>
              <input type="submit" name="qr" id="qr" value="确认留言"><?php //设置确认留言按钮 ?>
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