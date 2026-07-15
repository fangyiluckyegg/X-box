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

  // 🔥 这里直接用 mysqli，彻底不报错
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

if ((isset($_GET['P_ID'])) && ($_GET['P_ID'] != "")) {
  $deleteSQL = sprintf("DELETE FROM reply WHERE R_Post=%s",
                       GetSQLValueString($_GET['P_ID'], "int"));

  mysqli_query($conn, $deleteSQL) or die(mysqli_error($conn));
}

if ((isset($_GET['P_ID'])) && ($_GET['P_ID'] != "")) {
  $deleteSQL = sprintf("DELETE FROM post WHERE P_ID=%s",
                       GetSQLValueString($_GET['P_ID'], "int"));

  mysqli_query($conn, $deleteSQL) or die(mysqli_error($conn));

  $deleteGoTo = "index.php";
  header("Location: $deleteGoTo");
  ob_end_flush();
  exit;
}
?>
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>无标题文档</title>
</head>
<body>
</body>
</html>