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

$maxRows_rspic = 4;
$pageNum_rspic = 0;
if (isset($_GET['pageNum_rspic'])) {
  $pageNum_rspic = $_GET['pageNum_rspic'];
}
$startRow_rspic = $pageNum_rspic * $maxRows_rspic;

$colname_rspic = "-1";
if (isset($row_rstype['t_id'])) {
  $colname_rspic = $row_rstype['t_id'];
}

mysqli_select_db($conn, $database_conn);
$query_rspic = sprintf("SELECT * FROM work_pic WHERE t_id = %s ORDER BY p_id DESC", GetSQLValueString($colname_rspic, "int"));
$query_limit_rspic = sprintf("%s LIMIT %d, %d", $query_rspic, $startRow_rspic, $maxRows_rspic);
$rspic = mysqli_query($conn, $query_limit_rspic) or die(mysqli_error($conn));
$row_rspic = mysqli_fetch_assoc($rspic);

if (isset($_GET['totalRows_rspic'])) {
  $totalRows_rspic = $_GET['totalRows_rspic'];
} else {
  $all_rspic = mysqli_query($conn, $query_rspic);
  $totalRows_rspic = mysqli_num_rows($all_rspic);
}
$totalPages_rspic = ceil($totalRows_rspic/$maxRows_rspic)-1;
?>

<?php do { ?>
<?php if ($totalRows_rspic > 0) { ?>
<div class="work1">
  <a href="work-show.php?p_id=<?php echo $row_rspic['p_id']; ?>" target="_blank">

    <!-- 绝对正确路径 -->
    <img src="admin/upload/<?php echo $row_rspic['p_src']; ?>" alt="" width="195" height="122">

  </a><br>
  <span class="font02"><?php echo $row_rspic['p_name']; ?></span><br>
  <?php echo $row_rspic['p_date']; ?>
</div>
<?php } ?>
<?php } while ($row_rspic = mysqli_fetch_assoc($rspic)); ?>

<?php mysqli_free_result($rspic); ?>

