<?php
if (!isset($_SESSION)) {
    session_start();
}
require_once('Connections/conn.php');

// 安全查询，不依赖 P_ID
mysqli_select_db($conn, $database_conn);
$query_rsreply = "SELECT * FROM reply LIMIT 10";
$rsreply = mysqli_query($conn, $query_rsreply) or die(mysqli_error($conn));
$row_rsreply = mysqli_fetch_assoc($rsreply);
$totalRows_rsreply = mysqli_num_rows($rsreply);
?>

<?php if ($totalRows_rsreply > 0) { ?>
<div id="reply">
<?php do { ?>
<div id="reply-text"><strong>管理员回复：</strong><?php echo htmlspecialchars($row_rsreply['R_Content'], ENT_QUOTES, 'UTF-8'); ?></div>
<div id="reply-date"><?php echo htmlspecialchars($row_rsreply['R_Date'], ENT_QUOTES, 'UTF-8'); ?></div>
<?php } while ($row_rsreply = mysqli_fetch_assoc($rsreply)); ?>
</div>
<?php } ?>

<?php
mysqli_free_result($rsreply);
?>