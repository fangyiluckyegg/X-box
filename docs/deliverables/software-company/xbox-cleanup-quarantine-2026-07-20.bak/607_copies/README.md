# 隔离说明：607/Dad‘s 下的 - 副本 死副本

## 隔离时间
2025-07-14（P1 安全主线收尾 · 第三批）

## 来源目录
`Niu_Txl/607/Dad‘s/`

## 被隔离文件（原路径 → 本目录）
| 原路径 | 目标路径 |
| --- | --- |
| `607/Dad‘s/index - 副本.php` | `607_copies/index - 副本.php.disabled` |
| `607/Dad‘s/index - 副本.html` | `607_copies/index - 副本.html` |
| `607/Dad‘s/index - 副本 (2).html` | `607_copies/index - 副本 (2).html` |

## 隔离原因
1. 命名含 ` - 副本` / ` - 副本 (2)`（操作系统级手动副本），且同目录存在同名权威源文件
   （`index.php` / `index.html`）。
2. 经 **md5 校验，三份副本与各自权威源字节完全一致**（md5 相同）：
   - `index - 副本.php`      == `index.php`      （`bfa11fbc573946beecc99fa2b0f62285`）
   - `index - 副本.html`     == `index.html`     （`c4fe7d2323cc3c36df49433e5df1f15c`）
   - `index - 副本 (2).html` == `index.html`     （`c4fe7d2323cc3c36df49433e5df1f15c`）
3. 副本不包含任何权威源之外的独立业务逻辑。

结论：均为**字节级重复死副本**，按「隔离而非硬删」铁律移动至此，保留可还原。

## 还原方式
如需还原，将本目录下对应文件移回 `Niu_Txl/607/Dad‘s/` 即可。
