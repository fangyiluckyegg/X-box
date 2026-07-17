package com.prj.service.compare;

/**
 * 比对编排服务接口。
 *
 * <p>实现：{@link com.prj.service.compare.impl.CompareServiceImpl}（异步 worker，标注 @Async）。
 */
public interface ICompareService
{
    /**
     * 异步比对入口（实现类方法标注 @Async("compareTaskExecutor")）。
     * 编排：读 Excel → 向量化 → 相似度比对 → 写结果/进度；任意异常经 IProgressStore 记失败。
     *
     * <p>入参为请求线程内已读取的字节数组，避免依赖 Tomcat 上传临时文件——
     * 202 返回后请求线程结束、临时文件被清理，异步 worker 若直接读 MultipartFile 会因文件不存在抛 IOException。
     *
     * @param username   当前登录用户名（进度/结果键）
     * @param originExcel 原始 Excel 文件字节数组（请求线程内 getBytes 得到）
     * @param newExcel   比对 Excel 文件字节数组（请求线程内 getBytes 得到）
     */
    void performCompare(String username, byte[] originExcel, byte[] newExcel);
}
