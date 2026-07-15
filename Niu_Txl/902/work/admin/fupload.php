<?php
/**
 * 安全转义辅助函数（P1 安全加固：反射型 XSS 闭环）。
 */
function esc_attr($value) {
    // 用于 HTML 属性（例如 value 属性）上下文转义。
    return htmlspecialchars((string) $value, ENT_QUOTES, 'UTF-8');
}

function esc_js_str($value) {
    // 用于嵌在 HTML 属性内的 JS 单引号字符串字面量上下文（如 onSubmit 事件处理器中的参数）。
    // 先转义 JS 单引号字符串特殊字符，再做 HTML 属性转义，防止 HTML + JS 双层注入。
    $value = (string) $value;
    $search  = array(chr(92), chr(39), chr(13), chr(10), chr(9));
    $replace = array(chr(92) . chr(92), chr(92) . chr(39), ' ', ' ', ' ');
    $value = str_replace($search, $replace, $value);
    return htmlspecialchars($value, ENT_COMPAT, 'UTF-8');
}
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>文件上传页面</title>
</head>
<script language="JavaScript">
<!--
//检查上传内容 checkFileUpload(表单名称,文件类型,是否需要上传,文件大小)
function checkFileUpload(form,extensions,requireUpload,sizeLimit) {
  document.MM_returnValue = true;
  if (extensions != '') var re = new RegExp("\.(" + extensions.replace(/,/gi,"|") + ")$","i");
  for (var i = 0; i<form.elements.length; i++) {
    field = form.elements[i];
    if (field.type.toUpperCase() != 'FILE') continue;
    if (field.value == '') {
      if (requireUpload) {alert('请选择上传的文件！');
	  document.MM_returnValue = false;field.focus();break;}
    } else {
      if(extensions != '' && !re.test(field.value)) {
        alert('这个文件不符合上传的类型！\n只有以下的类型才允许上传： ' + extensions + '。\n请根据规定选择新的文件。');
        document.MM_returnValue = false;field.focus();break;
      }
    document.PU_uploadForm = form;
	
    re = new RegExp(".(gif|jpg|png|bmp|jpeg)$","i");
    if(re.test(field.value)&&sizeLimit!='') {
     setTimeout('if (document.MM_returnValue) document.PU_uploadForm.submit()',500);
     checkImageDimensions(field.value,sizeLimit);
    } } }
}

function showImageDimensions() {
  if (this.sizeLimit != '' && this.fileSize/15000 > this.sizeLimit) {
    alert('您所上传的文件为 '+this.fileSize/15000+' KB太大了！\n最大不可超过 ' + this.sizeLimit + ' KB'); return;}
  document.MM_returnValue = true;
}

function checkImageDimensions(fileName,sizeLimit) { //v2.0
  //document.MM_returnValue = false;
  var imgURL = 'file:///' + fileName, img = new Image();
  img.sizeLimit = sizeLimit;
  img.onload = showImageDimensions;
  img.src = imgURL;
}
//-->
</script>
<style type="text/css">
<!--
form {
	margin: 0px;
}
.formword {
	font-family: "Georgia", "Times New Roman", "Times", "serif";
	font-size: 14px;
}
.box {
	border: 1px dotted #333333;
}
-->
</style>
</head>
<body bgcolor="#EEEEEE" text="#333333" leftmargin="2" topmargin="2" marginwidth="2" marginheight="2">
<script language="JavaScript" type="text/JavaScript">
var windowW = 400;
var windowH = 200;
windowX = Math.ceil( (window.screen.width  - windowW) / 2 );
windowY = Math.ceil( (window.screen.height - windowH) / 2 );
window.resizeTo( Math.ceil( windowW ) , Math.ceil( windowH ) );
window.moveTo( Math.ceil( windowX ) , Math.ceil( windowY ) );
</script>
<form ACTION="fupaction.php" METHOD="POST" name="form1" enctype="multipart/form-data" onSubmit="checkFileUpload(this,'GIF,JPG,JPEG,PNG',true,'<?php echo esc_js_str(isset($_GET['ImgS']) ? $_GET['ImgS'] : ''); ?>','','','<?php echo esc_js_str(isset($_GET['ImgW']) ? $_GET['ImgW'] : ''); ?>','<?php echo esc_js_str(isset($_GET['ImgH']) ? $_GET['ImgH'] : ''); ?>','','');return document.MM_returnValue">
  <table width="100%" height="100%" border="0" cellpadding="4" cellspacing="0">
    <tr> 
      <td height="20"><table width="100%" border="0" cellpadding="4" cellspacing="0" bgcolor="#999999">
          <tr valign="baseline" class="formword"> 
            <td width="60" align="right"><font color="#FFFFFF">注意：</font></td>
            <td><font color="#FFFFFF"> 请选择图片上传，允许类型为GIF、JPG、JPEG、PNG</font></td>
          </tr>
        </table>
        
      </td>
    </tr>
    <tr> 
      <td height="20" align="center"> 
        <table border="0" cellpadding="4" cellspacing="0">
          <tr> 
            <td><input name="file" type="file" class="formword" id="file" size="40"></td>
          </tr>
        </table>
        <input name="Submit" type="submit" class="formword" value="开始上传"> <input name="close" type="button" class="formword" onClick="window.close();" value="关闭视窗">
        <input name="useForm" type="hidden" id="useForm" value="<?php echo esc_attr(isset($_GET['useForm']) ? $_GET['useForm'] : ''); ?>">
        <input name="upUrl" type="hidden" id="upUrl" value="<?php echo esc_attr(isset($_GET['upUrl']) ? $_GET['upUrl'] : ''); ?>">
        <input name="prevImg" type="hidden" id="prevImg" value="<?php echo esc_attr(isset($_GET['prevImg']) ? $_GET['prevImg'] : ''); ?>">
        <input name="reItem" type="hidden" id="reItem" value="<?php echo esc_attr(isset($_GET['reItem']) ? $_GET['reItem'] : ''); ?>">
      </td>
    </tr>
  </table>
</form>
</body>
</html>
