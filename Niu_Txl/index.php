<?php
// Niu_Txl 根入口：重定向到实际班级站，避免暴露 phpinfo()
header('Location: /902/message/index.php', true, 301);
exit;
