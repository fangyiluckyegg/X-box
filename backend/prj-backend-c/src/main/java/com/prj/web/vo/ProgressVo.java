package com.prj.web.vo;

/**
 * 比对进度视图对象（VO）。
 *
 * <p>前后端共用：后端经 {@code IProgressStore} 写入进度，前端轮询 {@code GET /api/excel/progress} 读取。
 * 字段名严格对齐前端 {@code progressInfo} 与 el-table 展示需求（原 CompareController 用 done/total，
 * 重构后统一为 current/total，前端同步调整展示）。
 *
 * <p>stage 枚举（前后端共用）：uploaded → vector_calc → match_compare → done / failed。
 * 仅当 stage=failed 时 {@link #message} 携带明确失败原因。
 */
public class ProgressVo
{
    /** 当前阶段：uploaded / vector_calc / match_compare / done / failed。 */
    private String stage = "";

    /** 完成百分比（0~100）。 */
    private int percent = 0;

    /** 已处理条数（前端展示为"当前/总"）。 */
    private int current = 0;

    /** 总条数。 */
    private int total = 0;

    /** 正在处理的文本（前端展示为 currentText）。 */
    private String currentText = "";

    /** 失败原因（仅 stage=failed 时填充）。 */
    private String message = "";

    public ProgressVo()
    {
    }

    public String getStage()
    {
        return stage;
    }

    public void setStage(String stage)
    {
        this.stage = stage == null ? "" : stage;
    }

    public int getPercent()
    {
        return percent;
    }

    public void setPercent(int percent)
    {
        this.percent = percent;
    }

    public int getCurrent()
    {
        return current;
    }

    public void setCurrent(int current)
    {
        this.current = current;
    }

    public int getTotal()
    {
        return total;
    }

    public void setTotal(int total)
    {
        this.total = total;
    }

    public String getCurrentText()
    {
        return currentText;
    }

    public void setCurrentText(String currentText)
    {
        this.currentText = currentText;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message == null ? "" : message;
    }
}
