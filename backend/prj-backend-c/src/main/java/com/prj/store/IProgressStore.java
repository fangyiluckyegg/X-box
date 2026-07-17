package com.prj.store;

import com.prj.web.vo.CompareResultRow;
import com.prj.web.vo.ProgressVo;

import java.util.List;

/**
 * 比对进度/结果存储接口（接口化，便于后续 Redis 化，见 P2-1）。
 *
 * <p>本轮提供内存实现 {@link com.prj.store.impl.InMemoryProgressStore}；后续可加
 * {@code RedisProgressStore} 支持多实例水平扩展，Controller/Service 仅依赖本接口，无需改动。
 *
 * <p>键约定：本轮沿用 username 单任务键（重提覆盖旧任务，见 {@link #cancel}）。
 */
public interface IProgressStore
{
    /** 覆盖写入进度（同一 username 多次提交会覆盖）。 */
    void saveProgress(String username, ProgressVo progress);

    /** 覆盖写入结果（同一 username 多次提交会覆盖）。 */
    void saveResult(String username, List<CompareResultRow> result);

    /** 读取进度；无则返回 null。 */
    ProgressVo getProgress(String username);

    /** 读取结果；无则返回 null。 */
    List<CompareResultRow> getResult(String username);

    /** 标记失败：stage=failed 并写入明确原因 message。 */
    void markFailed(String username, String message);

    /** 删除该用户进度与结果（重提覆盖旧任务）。 */
    void cancel(String username);
}
