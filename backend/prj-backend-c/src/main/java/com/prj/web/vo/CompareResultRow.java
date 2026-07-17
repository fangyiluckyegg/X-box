package com.prj.web.vo;

/**
 * 比对结果单行视图对象（VO）。
 *
 * <p>字段名严格对齐前端 el-table 列 prop 与 {@code downloadResult} 导出列，禁止随意改名，
 * 否则前端列渲染与 Excel 导出会错位。
 *
 * <p>字段固定为：name / originVal / matchedName / newVal / similarity / diffType。
 * diffType 取值：完全匹配 / 语义模糊匹配 / 未匹配 / 新增项。
 */
public class CompareResultRow
{
    /** 原始名称（origin 侧文本，对应 el-table 隐藏主键）。 */
    private String name = "";

    /** 原始数据值。 */
    private String originVal = "";

    /** 匹配到的名称（new 侧文本）。 */
    private String matchedName = "";

    /** 新比对数据值。 */
    private String newVal = "";

    /** 相似度（0~1，保留两位）。 */
    private double similarity = 0.0;

    /** 差异类型：完全匹配 / 语义模糊匹配 / 未匹配 / 新增项。 */
    private String diffType = "";

    public CompareResultRow()
    {
    }

    public CompareResultRow(String name, String originVal, String matchedName,
                            String newVal, double similarity, String diffType)
    {
        this.name = name;
        this.originVal = originVal;
        this.matchedName = matchedName;
        this.newVal = newVal;
        this.similarity = similarity;
        this.diffType = diffType;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name == null ? "" : name;
    }

    public String getOriginVal()
    {
        return originVal;
    }

    public void setOriginVal(String originVal)
    {
        this.originVal = originVal == null ? "" : originVal;
    }

    public String getMatchedName()
    {
        return matchedName;
    }

    public void setMatchedName(String matchedName)
    {
        this.matchedName = matchedName == null ? "" : matchedName;
    }

    public String getNewVal()
    {
        return newVal;
    }

    public void setNewVal(String newVal)
    {
        this.newVal = newVal == null ? "" : newVal;
    }

    public double getSimilarity()
    {
        return similarity;
    }

    public void setSimilarity(double similarity)
    {
        this.similarity = similarity;
    }

    public String getDiffType()
    {
        return diffType;
    }

    public void setDiffType(String diffType)
    {
        this.diffType = diffType == null ? "" : diffType;
    }
}
