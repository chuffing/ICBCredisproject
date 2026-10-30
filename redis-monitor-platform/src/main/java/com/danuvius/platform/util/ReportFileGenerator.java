package com.danuvius.platform.util;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.model.dto.alarm.record.AlarmStatsDTO;
import com.danuvius.platform.model.dto.monitor.ReportEventDTO;
import com.danuvius.platform.model.dto.monitor.ReportOverviewDTO;
import com.danuvius.platform.model.dto.monitor.ReportQueryResponse;
import com.danuvius.platform.model.dto.monitor.ReportTrendDTO;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.danuvius.platform.util.PdfUtils.*;

/**
 * 报告文件生成器
 * 导出内容与查询条件一致：概览统计、指标趋势图、告警与切换明细、节点可用率
 */
@Slf4j
@Component
public class ReportFileGenerator {

    /** 可选的内置中文字体；不存在或损坏时回退到 OpenPDF 自带 CJK 字体 */
    private static final String BUILTIN_FONT_PATH = "/fonts/simhei.ttf";
    private static BaseFont CN_FONT;
    private static final Object FONT_LOCK = new Object();

    private static final Color TEXT_COLOR = new Color(0x17, 0x20, 0x33);
    private static final Color MUTED_COLOR = new Color(0x90, 0x93, 0x99);
    private static final Color BORDER_COLOR = new Color(0xEB, 0xEE, 0xF5);
    private static final Color GRID_COLOR = new Color(0xF0, 0xF3, 0xF8);
    private static final Color HEADER_BG = new Color(0xFA, 0xFA, 0xFA);


    /**
     * 生成导出文件，实例名称缺省时展示实例 id
     */
    public File generate(ReportQueryResponse report, String instanceId,
                         String reportType, String exportDir) throws IOException {
        return generate(report, instanceId, null, reportType, exportDir);
    }

    /**
     * 生成导出文件
     * @param instanceName 实例展示名称，可为空
     * @return 生成的文件
     */
    public File generate(ReportQueryResponse report, String instanceId, String instanceName,
                         String reportType, String exportDir) throws IOException {
        File dir = new File(exportDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("创建导出目录失败: " + exportDir);
        }
        File file = new File(dir, "Report-" + instanceId + "-" + reportType + "-"
                + System.currentTimeMillis() + ".pdf");
        writePdf(file, report, instanceId, instanceName, reportType);
        return file;
    }


    private BaseFont cnBaseFont() throws IOException {
        if (CN_FONT == null) {
            synchronized (FONT_LOCK) {
                if (CN_FONT == null) {
                    CN_FONT = loadChineseFont();
                }
            }
        }
        return CN_FONT;
    }

    /**
     * 加载中文字体
     * 1) 优先使用 classpath 内置 TTF（若项目自带且文件完整）；
     * 2) 其次使用操作系统常见中文字体（Windows/Linux）；
     * 3) 最后回退到 OpenPDF 自带的 STSong-Light/UniGB-UCS2-H
     * 这样即使 classpath 中的字体文件缺失/被截断，也不会因 EOFException 导致整个导出任务失败
     */
    private BaseFont loadChineseFont() throws IOException {
        BaseFont font = loadClasspathFont();
        if (font != null) {
            return font;
        }
        font = loadSystemFont();
        if (font != null) {
            return font;
        }

        try {
            BaseFont fallback = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.EMBEDDED);
            log.info("[报告导出] 使用 OpenPDF 内置中文字体 STSong-Light");
            return fallback;
        } catch (Exception e) {
            throw new IOException("未找到可用的中文字体，无法生成 PDF 报告", e);
        }
    }

    private BaseFont loadClasspathFont() {
        try (InputStream in = getClass().getResourceAsStream(BUILTIN_FONT_PATH)) {
            if (in == null) {
                log.warn("[报告导出] classpath 中未找到 {}，尝试使用系统字体", BUILTIN_FONT_PATH);
                return null;
            }
            byte[] fontBytes = org.springframework.util.StreamUtils.copyToByteArray(in);
            if (fontBytes.length < 1024) {
                log.warn("[报告导出] 内置字体文件过小({} bytes)，疑似损坏，尝试使用系统字体",
                        fontBytes.length);
                return null;
            }
            try {
                BaseFont font = BaseFont.createFont("simhei.ttf", BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED, BaseFont.CACHED, fontBytes, null);
                log.info("[报告导出] 使用内置中文字体 {}", BUILTIN_FONT_PATH);
                return font;
            } catch (Exception e) {
                log.warn("[报告导出] 内置字体 {} 无法解析，尝试使用系统字体", BUILTIN_FONT_PATH, e);
                return null;
            }
        } catch (Exception e) {
            log.warn("[报告导出] 读取内置字体 {} 失败，尝试使用系统字体", BUILTIN_FONT_PATH, e);
            return null;
        }
    }

    /**
     * 读取操作系统常见中文字体仅用于本机部署兜底；
     * 生产环境建议在 classpath 放置完整的开源中文字体（如 Noto Sans SC）
     */
    private BaseFont loadSystemFont() {
        String winDir = System.getenv("WINDIR");
        if (winDir == null || winDir.trim().isEmpty()) {
            winDir = "C:\\Windows";
        }
        String[] candidates = {
                // Windows
                winDir + "\\Fonts\\simhei.ttf",
                winDir + "\\Fonts\\msyh.ttc",
                winDir + "\\Fonts\\simsun.ttc",
                // Linux
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/arphic/uming.ttc"
        };
        for (String path : candidates) {
            File file = new File(path);
            if (!file.isFile() || !file.canRead()) {
                continue;
            }
            try {
                byte[] fontBytes = Files.readAllBytes(file.toPath());
                if (fontBytes.length < 1024) {
                    continue;
                }
                String fontName = file.getName();
                if (fontName.toLowerCase().endsWith(".ttc")) {
                    // TTC 字体集合需要指定子字体索引
                    fontName = fontName + ",0";
                }
                BaseFont font = BaseFont.createFont(fontName, BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED, BaseFont.CACHED, fontBytes, null);
                log.info("[报告导出] 使用系统字体 {}", file.getAbsolutePath());
                return font;
            } catch (Exception e) {
                log.warn("[报告导出] 系统字体 {} 无法加载，继续尝试下一个", path, e);
            }
        }
        log.warn("[报告导出] 未找到可用的系统中文 TTF/TTC 字体，回退到 OpenPDF 内置中文字体");
        return null;
    }

    // ---------------------------------------------------------------- PDF
    private void writePdf(File file, ReportQueryResponse report, String instanceId,
                          String instanceName, String reportType) throws IOException {
        Document document = new Document(PageSize.A4, 34, 34, 34, 34);
        try (FileOutputStream out = new FileOutputStream(file)) {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            try {
                BaseFont baseFont = cnBaseFont();
                Font h1 = new Font(baseFont, 18, Font.BOLD, TEXT_COLOR);
                Font h2 = new Font(baseFont, 12, Font.BOLD, TEXT_COLOR);
                Font small = new Font(baseFont, 8, Font.NORMAL, MUTED_COLOR);
                Font cardTitle = new Font(baseFont, 8, Font.NORMAL, MUTED_COLOR);
                Font cardValue = new Font(baseFont, 16, Font.BOLD, TEXT_COLOR);
                Font cardDesc = new Font(baseFont, 7.5f, Font.NORMAL, MUTED_COLOR);
                Font tableHeader = new Font(baseFont, 8, Font.BOLD, MUTED_COLOR);
                Font tableBody = new Font(baseFont, 8, Font.NORMAL, TEXT_COLOR);

                String displayName = (instanceName != null && !instanceName.trim().isEmpty())
                        ? instanceName.trim() : instanceId;

                Paragraph title = new Paragraph("运行报告", h1);
                title.setSpacingAfter(2f);
                document.add(title);
                Paragraph subtitle = new Paragraph(
                        "实例：" + displayName + "　统计范围：" + reportRange(reportType)
                                + "　报告类型：" + reportType, small);
                subtitle.setSpacingAfter(10f);
                document.add(subtitle);

                // 与历史监控页保持一致：运行报告概览卡片
                document.add(buildOverviewTable(report.getOverview(),
                        cardTitle, cardValue, cardDesc));

                // 与历史监控页保持一致：监控指标统计
                document.add(sectionHeading("监控指标统计", h2));
                Paragraph chartDesc = new Paragraph("展示报告周期内主要Redis监控指标变化", small);
                chartDesc.setSpacingAfter(6f);
                document.add(chartDesc);
                document.add(buildChartTable(writer, baseFont, report.getTrendList(),
                        reportType, cardDesc));

                // 与历史监控页保持一致：告警与主从切换统计
                document.add(sectionHeading("告警与主从切换统计", h2));
                Paragraph eventDesc = new Paragraph("展示报告周期内告警及主从切换情况", small);
                eventDesc.setSpacingAfter(6f);
                document.add(eventDesc);
                document.add(buildEventTable(report.getEventList(), tableHeader, tableBody));
            } finally {
                try {
                    document.close();
                } catch (Exception closeEx) {
                    log.warn("[报告导出] 关闭 PDF 文档失败", closeEx);
                }
            }
        } catch (Exception e) {
            throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(),"生成 PDF 失败: ",e.getMessage());
        }
    }

    private Paragraph sectionHeading(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(12f);
        paragraph.setSpacingAfter(4f);
        return paragraph;
    }

    /**
     * 8 项运行报告概览卡片，与历史监控页卡片字段和顺序保持一致
     */
    private PdfPTable buildOverviewTable(ReportOverviewDTO ov, Font titleFont,
                                         Font valueFont, Font descFont) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingBefore(2f);
        table.setSpacingAfter(10f);
        table.setKeepTogether(true);

        AlarmStatsDTO stats = ov == null ? null : ov.getAlarmStatsDTO();
        addOverviewCard(table, "CPU平均使用率",
                withUnit(percent(ov == null ? null : ov.getCpuAvg()), "%"),
                "峰值" + withUnit(percent(ov == null ? null : ov.getCpuMax()), "%")
                        + timeSuffix(ov == null ? null : ov.getCpuMaxTim()),
                titleFont, valueFont, descFont);
        addOverviewCard(table, "Redis内存平均使用量",
                withUnit(decimal(ov == null ? null : ov.getRedisMemAvg(), 2), "GB"),
                "峰值" + withUnit(decimal(ov == null ? null : ov.getRedisMemMax(), 2), "GB")
                        + timeSuffix(ov == null ? null : ov.getRedisMemMaxTim()),
                titleFont, valueFont, descFont);
        addOverviewCard(table, "QPS平均值",
                integer(ov == null ? null : ov.getQpsAvg()),
                "峰值" + integer(ov == null ? null : ov.getQpsMax())
                        + timeSuffix(ov == null ? null : ov.getQpsMaxTim()),
                titleFont, valueFont, descFont);
        addOverviewCard(table, "平均延迟",
                withUnit(decimal(ov == null ? null : ov.getLatencyAvg(), 2), "ms"),
                "峰值" + withUnit(decimal(ov == null ? null : ov.getLatencyMax(), 2), "ms")
                        + timeSuffix(ov == null ? null : ov.getLatencyMaxTim()),
                titleFont, valueFont, descFont);
        addOverviewCard(table, "告警次数",
                integer(stats == null ? null : stats.getAlarmTotal()),
                "严重" + integer(stats == null ? null : stats.getSeverityCount())
                        + "｜警告" + integer(stats == null ? null : stats.getAlarmCount())
                        + "｜提示" + integer(stats == null ? null : stats.getInfoCount()),
                titleFont, valueFont, descFont);
        addOverviewCard(table, "主从切换次数",
                integer(ov == null ? null : ov.getSwitchTotal()),
                "最近切换：" + timeText(ov == null ? null : ov.getLastSwitchTime()),
                titleFont, valueFont, descFont);
        addOverviewCard(table, "Redis服务可用率",
                withUnit(decimal(ov == null ? null : ov.getServiceAvailableRate(), 2), "%"),
                "整体运行稳定",
                titleFont, valueFont, descFont);
        addOverviewCard(table, "当前节点数",
                String.valueOf(longValue(ov == null ? null : ov.getMasterCount())
                        + longValue(ov == null ? null : ov.getReplicaCount())),
                "MASTER" + longValue(ov == null ? null : ov.getMasterCount())
                        + "｜REPLICA" + longValue(ov == null ? null : ov.getReplicaCount()),
                titleFont, valueFont, descFont);
        return table;
    }

    private void addOverviewCard(PdfPTable table, String title, String value,
                                 String description, Font titleFont,
                                 Font valueFont, Font descFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER_COLOR);
        cell.setBorderWidth(0.5f);
        cell.setPadding(7f);
        cell.setMinimumHeight(58f);

        cell.addElement(new Paragraph(title, titleFont));
        Paragraph valueParagraph = new Paragraph(value, valueFont);
        valueParagraph.setSpacingBefore(3f);
        cell.addElement(valueParagraph);
        Paragraph descParagraph = new Paragraph(description, descFont);
        descParagraph.setSpacingBefore(3f);
        cell.addElement(descParagraph);
        table.addCell(cell);
    }

    /**
     * 4 张趋势图，按 2×2 排列，与历史监控页监控指标统计一致
     */
    private PdfPTable buildChartTable(PdfWriter writer, BaseFont baseFont,
                                      List<ReportTrendDTO> trends, String reportType,
                                      Font descFont) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(2f);
        table.setSpacingAfter(8f);

        String[][] defs = {
                {"cpuUsage", "CPU使用率", "平均值及峰值变化", "#409eff"},
                {"redisMemUsed", "Redis内存使用量", "Redis内存使用变化", "#20a86a"},
                {"qps", "QPS", "Redis吞吐量变化", "#7053d8"},
                {"avgLatency", "请求延迟", "Redis平均响应延迟", "#e39b34"}
        };
        Font chartTitle = new Font(baseFont, 9, Font.BOLD, TEXT_COLOR);
        for (String[] def : defs) {
            ReportTrendDTO trend = findTrend(trends, def[0]);
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(BORDER_COLOR);
            cell.setBorderWidth(0.5f);
            cell.setPadding(7f);
            cell.addElement(new Paragraph(def[1], chartTitle));
            Paragraph desc = new Paragraph(def[2], descFont);
            desc.setSpacingBefore(2f);
            cell.addElement(desc);

            PdfTemplate template = buildLineChart(writer, baseFont, trend, reportType,
                    Color.decode(def[3]));
            Image image = Image.getInstance(template);
            image.setWidthPercentage(100f);
            cell.addElement(image);
            table.addCell(cell);
        }
        return table;
    }

    private ReportTrendDTO findTrend(List<ReportTrendDTO> trends, String metricKey) {
        if (trends == null) {
            return null;
        }
        for (ReportTrendDTO trend : trends) {
            if (metricKey.equals(trend.getMetricKey())) {
                return trend;
            }
        }
        return null;
    }

    /**
     * 使用 PdfTemplate 绘制矢量折线图，与历史监控页 ECharts 保持样式一致
     * 0 基线、5 段左右的整齐纵轴刻度、4 个横轴时间标签、平滑折线和浅色面积填充
     */
    private PdfTemplate buildLineChart(PdfWriter writer, BaseFont baseFont,
                                       ReportTrendDTO trend, String reportType,
                                       Color lineColor) throws Exception {
        float width = 250f;
        float height = 145f;
        PdfTemplate template = PdfTemplate.createTemplate(writer, width, height);
        template.setLineWidth(0.6f);
        template.setColorStroke(BORDER_COLOR);
        template.rectangle(0.5f, 0.5f, width - 1, height - 1);
        template.stroke();

        List<String> times = trend == null ? null : trend.getTimestampList();
        List<Double> values = trend == null ? null : trend.getValueList();
        if (times == null || values == null || times.isEmpty() || values.isEmpty()) {
            drawNoData(template, baseFont, width, height);
            return template;
        }

        int count = Math.min(times.size(), values.size());
        double max = -Double.MAX_VALUE;
        int validCount = 0;
        for (int i = 0; i < count; i++) {
            Double value = values.get(i);
            if (value == null) {
                continue;
            }
            max = Math.max(max, value);
            validCount++;
        }
        if (validCount < 2) {
            drawNoData(template, baseFont, width, height);
            return template;
        }

        // 与页面 ECharts 的 min: 0 对齐，并生成整齐的纵轴刻度
        max = Math.max(max, 0d);
        double step = niceStep(max / 5.0);
        double niceMax = Math.ceil(max / step) * step;
        if (step < 100) {
            // 与 ECharts 自动取整类似：内存 8 -> 10、CPU 38 -> 50、延迟 2.6 -> 3
            niceMax = Math.max(niceMax, step * 5);
        }
        if (niceMax <= 0) {
            niceMax = step;
        }
        int intervals = (int) Math.round(niceMax / step);
        if (intervals < 1) {
            intervals = 1;
        }

        float left = 32f;
        float right = width - 8f;
        float top = height - 10f;
        float bottom = 24f;

        List<List<float[]>> segments = buildChartPoints(values, count,
                left, right, bottom, top, niceMax);

        // 浅色渐变面积：先画面积，再画网格/折线，避免遮住网格
        Color areaTop = tint(lineColor, 0.84f);
        Color areaBottom = tint(lineColor, 0.97f);
        PdfShading shading = PdfShading.simpleAxial(writer, 0, top, 0, bottom,
                areaTop, areaBottom, false, false);
        template.setShadingFill(new PdfShadingPattern(shading));
        for (List<float[]> segment : segments) {
            if (segment.size() < 2) {
                continue;
            }
            appendSmoothPath(template, segment);
            float[] last = segment.get(segment.size() - 1);
            float[] first = segment.get(0);
            template.lineTo(last[0], bottom);
            template.lineTo(first[0], bottom);
            template.closePath();
            template.fill();
        }

        // 网格 + 纵轴刻度
        template.setLineWidth(0.3f);
        template.setColorStroke(GRID_COLOR);
        for (int i = 0; i <= intervals; i++) {
            float y = top - (top - bottom) * i / intervals;
            template.moveTo(left, y);
            template.lineTo(right, y);
            template.stroke();
        }
        template.setFontAndSize(baseFont, 6f);
        template.setColorFill(MUTED_COLOR);
        for (int i = 0; i <= intervals; i++) {
            float y = top - (top - bottom) * i / intervals;
            double value = niceMax - step * i;
            template.showTextAligned(Element.ALIGN_RIGHT, axisText(value, step),
                    left - 3, y - 2, 0);
        }

        // 横轴 4 个时间标签（周报显示 MM-dd HH:mm）
        int[] candidates = {0, Math.round((count - 1) / 3f),
                Math.round((count - 1) * 2f / 3f), count - 1};
        int lastIndex = -1;
        for (int index : candidates) {
            if (index <= lastIndex || index >= count) {
                continue;
            }
            float x = left + (right - left) * index / (count - 1f);
            int alignment = index == 0 ? Element.ALIGN_LEFT
                    : (index == count - 1 ? Element.ALIGN_RIGHT : Element.ALIGN_CENTER);
            template.showTextAligned(alignment, shortTime(times.get(index), reportType),
                    x, bottom - 9, 0);
            lastIndex = index;
        }

        // 平滑折线
        template.setColorStroke(lineColor);
        template.setLineWidth(1.3f);
        for (List<float[]> segment : segments) {
            appendSmoothPath(template, segment);
            template.stroke();
        }
        return template;
    }

    /**
     * 将连续非 null 数据转换为绘图坐标；遇到 null 断开，避免跨空档连线
     */
    private List<List<float[]>> buildChartPoints(List<Double> values, int count,
                                                 float left, float right,
                                                 float bottom, float top,
                                                 double niceMax) {
        List<List<float[]>> segments = new ArrayList<>();
        List<float[]> current = null;
        for (int i = 0; i < count; i++) {
            Double value = values.get(i);
            if (value == null) {
                current = null;
                continue;
            }
            if (current == null) {
                current = new ArrayList<>();
                segments.add(current);
            }
            float x = left + (right - left) * i / (count - 1f);
            float y = bottom + (top - bottom) * (float) (value / niceMax);
            current.add(new float[]{x, y});
        }
        return segments;
    }

    /** 使用水平切线的三次贝塞尔曲线近似 ECharts 的 smooth 折线 */
    private void appendSmoothPath(PdfContentByte canvas, List<float[]> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        float[] first = points.get(0);
        canvas.moveTo(first[0], first[1]);
        for (int i = 1; i < points.size(); i++) {
            float[] previous = points.get(i - 1);
            float[] current = points.get(i);
            float dx = (current[0] - previous[0]) / 3f;
            canvas.curveTo(previous[0] + dx, previous[1],
                    current[0] - dx, current[1], current[0], current[1]);
        }
    }

    /** 生成 1/2/5×10^n 形式的“整齐”刻度间隔 */
    private double niceStep(double raw) {
        if (raw <= 0 || Double.isNaN(raw) || Double.isInfinite(raw)) {
            return 1;
        }
        double exponent = Math.floor(Math.log10(raw));
        double fraction = raw / Math.pow(10, exponent);
        double nice;
        if (fraction <= 1) {
            nice = 1;
        } else if (fraction <= 2) {
            nice = 2;
        } else if (fraction <= 6) {
            nice = 5;
        } else {
            nice = 10;
        }
        return nice * Math.pow(10, exponent);
    }

    private String axisText(double value, double step) {
        double normalized = Math.abs(value) < 0.0000001 ? 0 : value;
        if (step >= 0.1 && Math.abs(normalized) >= 1000) {
            return String.format(Locale.US, "%,.0f", normalized);
        }
        if (step >= 1) {
            return String.format(Locale.US, "%.0f", normalized);
        }
        String text = String.format(Locale.US, step >= 0.1 ? "%.1f" : "%.2f", normalized);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private Color tint(Color color, float whiteRatio) {
        int red = Math.round(color.getRed() + (255 - color.getRed()) * whiteRatio);
        int green = Math.round(color.getGreen() + (255 - color.getGreen()) * whiteRatio);
        int blue = Math.round(color.getBlue() + (255 - color.getBlue()) * whiteRatio);
        return new Color(red, green, blue);
    }

    private void drawNoData(PdfTemplate template, BaseFont baseFont, float width, float height) {
        template.setFontAndSize(baseFont, 7f);
        template.setColorFill(MUTED_COLOR);
        template.showTextAligned(Element.ALIGN_CENTER, "暂无数据", width / 2, height / 2, 0);
    }

    /**
     * 告警与主从切换统计表，列与历史监控页保持一致
     */
    private PdfPTable buildEventTable(List<ReportEventDTO> events, Font headerFont,
                                      Font bodyFont) {
        PdfPTable table = new PdfPTable(new float[]{1.1f, 1.6f, 3.2f, 1.5f, 1.0f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSpacingBefore(2f);

        addEventCell(table, "类型", headerFont, true);
        addEventCell(table, "Redis节点", headerFont, true);
        addEventCell(table, "内容", headerFont, true);
        addEventCell(table, "发生时间", headerFont, true);
        addEventCell(table, "状态", headerFont, true);

        if (events == null || events.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("暂无事件", bodyFont));
            empty.setColspan(5);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBorderColor(BORDER_COLOR);
            empty.setBorderWidth(0.5f);
            empty.setPadding(8f);
            table.addCell(empty);
            return table;
        }
        for (ReportEventDTO event : events) {
            addEventCell(table, text(event.getEventType()), bodyFont, false);
            addEventCell(table, text(event.getNodeDisplay()), bodyFont, false);
            addEventCell(table, text(event.getContent()), bodyFont, false);
            addEventCell(table, event.getHappenTime() == null
                    ? "--" : event.getHappenTime().format(CommonConstant.FORMATTER), bodyFont, false);
            addEventCell(table, text(event.getStatus()), bodyFont, false);
        }
        return table;
    }

    private void addEventCell(PdfPTable table, String value, Font font, boolean header) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setBorderColor(BORDER_COLOR);
        cell.setBorderWidth(0.5f);
        cell.setPadding(4f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (header) {
            cell.setBackgroundColor(HEADER_BG);
        }
        table.addCell(cell);
    }


}
