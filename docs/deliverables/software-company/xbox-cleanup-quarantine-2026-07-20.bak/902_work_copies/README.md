# 隔离说明：902/work 下的 copy 死副本

## 隔离时间
2025-07-14（P1 安全主线收尾 · 第三批）

## 来源目录
`Niu_Txl/902/work/`

## 被隔离文件（原路径 → 本目录）
| 原路径 | 目标路径 |
| --- | --- |
| `902/work/index copy.php` | `902_work_copies/index copy.php.disabled` |
| `902/work/list copy.php` | `902_work_copies/list copy.php.disabled` |
| `902/work/work-all copy.php` | `902_work_copies/work-all copy.php.disabled` |
| `902/work/work-show copy.php` | `902_work_copies/work-show copy.php.disabled` |
| `902/work/work-type copy.php` | `902_work_copies/work-type copy.php.disabled` |

## 隔离原因
1. 命名含 ` copy`（操作系统级手动副本），且同目录均存在同名权威源文件
   （`index.php` / `list.php` / `work-all.php` / `work-show.php` / `work-type.php`）。
2. 经全文比对，副本与权威源的差异**全部为**：
   - 副本使用已在 PHP 7.0+ 移除的 `mysql_*` 废弃函数（直接运行会 fatal error）；
     权威源已升级为 `mysqli_*` 并补充 `global $conn;`，是真正可用的版本。
   - 少量空白 / 格式化差异，以及 `index` 一处 `<title>` 文案差异。
3. 副本在**全部文件类型**（PHP / HTML 等）中均无任何 `include` / `require` / 链接引用，属于死代码。
4. 副本不包含任何权威源之外的独立业务逻辑。

结论：均为**陈旧死副本**，按「隔离而非硬删」铁律移动至此，保留可还原。

## 还原方式
如需还原，将本目录下对应文件移回 `Niu_Txl/902/work/` 即可。
注意：副本为陈旧的 `mysql` 版本，直跑会报错；建议还原前先合并权威源的最新 `mysqli` 逻辑。
