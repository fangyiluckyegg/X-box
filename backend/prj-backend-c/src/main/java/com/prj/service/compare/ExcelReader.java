package com.prj.service.compare;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 读取组件（从原 CompareController 抽离 POI 逻辑，单一职责）。
 *
 * <p>读取首列有效文本，自动跳过表头行（含"名称"/"单位"/"name"字样）；
 * 含行数上限防护（{@code MAX_EXCEL_ROWS=50000}）防恶意/超大文件导致 OOM（DoS）。
 */
@Component
public class ExcelReader
{
    /** Excel 读取行数硬上限（5万行），防止恶意/超大文件导致 OOM（DoS）。 */
    private static final int MAX_EXCEL_ROWS = 50_000;

    /**
     * 读取 Excel 首列所有非空文本（自动跳过表头行）。
     *
     * <p>入参为 Excel 文件流，调用方负责在请求线程内将 MultipartFile 读成字节并包装为
     * {@link ByteArrayInputStream} 传入，从而避免依赖 Tomcat 上传临时文件（异步场景下临时文件可能被清理）。
     *
     * @param inputStream Excel 文件输入流（由 byte[] 包装，非 MultipartFile 临时文件）
     * @return 首列有效文本列表（已去除首尾空白）
     * @throws IOException 文件流读取失败
     * @throws IllegalArgumentException 行数超过上限
     */
    public List<String> readFirstColumnNames(InputStream inputStream) throws IOException
    {
        List<String> res = new ArrayList<>();
        // zip bomb 防护：设置最小解压比阈值（默认 0.01，放宽到 0.005），避免解压膨胀耗尽内存。
        ZipSecureFile.setMinInflateRatio(0.005);
        try (Workbook wb = WorkbookFactory.create(inputStream))
        {
            Sheet sheet = wb.getSheetAt(0);
            // 行数上限防护：超过 MAX_EXCEL_ROWS 直接拒绝，避免 OOM（DoS）
            if (sheet.getLastRowNum() > MAX_EXCEL_ROWS)
            {
                throw new IllegalArgumentException("Excel 行数超过上限 " + MAX_EXCEL_ROWS);
            }
            boolean skipHead = true;
            for (int r = 0; r <= sheet.getLastRowNum(); r++)
            {
                // 双重保护：循环内再次校验当前行号，即便 LastRowNum 估算不准也不超量读取
                if (r > MAX_EXCEL_ROWS)
                {
                    throw new IllegalArgumentException("Excel 行数超过上限 " + MAX_EXCEL_ROWS);
                }
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell cell = row.getCell(0);
                if (cell == null) continue;
                String val = getCellValue(cell).trim();
                if (val.isEmpty()) continue;
                // 跳过含义为表头的首行
                if (skipHead)
                {
                    skipHead = false;
                    if (val.contains("名称") || val.contains("单位") || val.equalsIgnoreCase("name")) continue;
                }
                res.add(val);
            }
        }
        return res;
    }

    /**
     * 以字符串形式读取单元格内容（区分字符串/数值/布尔类型）。
     *
     * @param cell 目标单元格
     * @return 单元格的字符串值；空单元格或无法识别类型返回空串
     */
    private String getCellValue(Cell cell)
    {
        if (cell == null) return "";
        switch (cell.getCellType())
        {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                double num = cell.getNumericCellValue();
                return num == Math.floor(num) ? String.valueOf((long) num) : String.valueOf(num);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
