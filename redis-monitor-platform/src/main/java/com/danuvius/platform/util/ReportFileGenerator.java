package com.danuvius.platform.util;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 报告文件生成器。
 * 导出内容与查询条件一致：概览统计、指标趋势图、告警与切换明细、节点可用率。
 */
@Slf4j
@Component
public class ReportFileGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 可选的内置中文字体；不存在或损坏时回退到 OpenPDF 自带 CJK 字体 */
    private static final String BUILTIN_FONT_PATH = "/fonts/simhei.ttf";
    private static BaseFont CN_FONT;
    private static final Object FONT_LOCK = new Object();

    private static final Color TEXT_COLOR = new Color(0x17, 0x20, 0x33);
    private static final Color MUTED_COLOR = new Color(0x90, 0x93, 0x99);
    private static final Color BORDER_COLOR = new Color(0xEB, 0xEE, 0xF5);
    private static final Color GRID_COLOR = new Color(0xF0, 0xF3, 0xF8);
    private static final Color HEADER_BG = new Color(0xFA, 0xFA, 0xFA);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 生成导出文件（保留原有调用方式，实例名称缺省时展示实例 id）。
     */
    public File generate(ReportQueryResponse report, String instanceId,
                         String reportType, String exportDir) throws IOException {
        return generate(report, instanceId, null, reportType, exportDir);
    }

    /**
     * 生成导出文件。
     * @param instanceName 实例展示名称，可为空
     * @return 生成的文件
     */
    public File generate(ReportQueryResponse report, String instanceId, String instanceName,
                         String reportType, String exportDir) throws IOException {
        File dir = new File(exportDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("创建导出目录失败: " + exportDir);
        }
        File file = new File(dir, "redis-report-" + instanceId + "-" + reportType + "-"
                + System.currentTimeMillis() + ".pdf");
        writePdf(file, report, instanceId, instanceName, reportType);
        return file;
    }


    // ---------------------------------------------------------------- HTML

    private void writeHtml(File file, ReportQueryResponse report, String instanceId,
                           String reportType) throws IOException {
        ReportOverviewDTO ov = report.getOverview();
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<title>Redis 运行报告 - ").append(escape(reportType)).append("</title>")
                .append("<style>")
                .append("body{font-family:'Microsoft YaHei',Arial,sans-serif;margin:24px;color:#333;}")
                .append("h1{font-size:20px;} h2{font-size:16px;margin-top:24px;border-bottom:1px solid #ddd;padding-bottom:4px;}")
                .append(".cards{display:flex;flex-wrap:wrap;gap:12px;}")
                .append(".card{border:1px solid #e0e0e0;border-radius:6px;padding:10px 14px;min-width:150px;}")
                .append(".card .avg{font-size:20px;font-weight:bold;}")
                .append(".card .peak{font-size:12px;color:#888;}")
                .append("table{border-collapse:collapse;width:100%;font-size:12px;}")
                .append("th,td{border:1px solid #ddd;padding:4px 8px;text-align:left;}")
                .append("th{background:#f5f5f5;}")
                .append("</style></head><body>");

        sb.append("<h1>Redis 运行报告（").append(escape(reportType)).append("）</h1>");
        sb.append("<p>实例：").append(escape(instanceId)).append("　生成时间：")
                .append(LocalDateTime.now().format(FORMATTER)).append("</p>");

        sb.append("<h2>一、概览统计</h2>");
        if (ov != null) {
            sb.append("<div class=\"cards\">");
            appendCard(sb, "CPU平均使用率(%)", ov.getCpuAvg(), ov.getCpuMax(), ov.getCpuMaxTim());
            appendCard(sb, "Redis内存平均使用量(GB)", ov.getRedisMemAvg(), ov.getRedisMemMax(), ov.getRedisMemMaxTim());
            appendCard(sb, "QPS平均值(次/秒)", ov.getQpsAvg(), ov.getQpsMax(), ov.getQpsMaxTim());
            appendCard(sb, "平均延迟(ms)", ov.getLatencyAvg(), ov.getLatencyMax(), ov.getLatencyMaxTim());
            sb.append("</div>");
            sb.append("<div class=\"cards\">");
            appendPlainCard(sb, "告警总次数", ov.getAlarmStatsDTO() == null
                    ? null : ov.getAlarmStatsDTO().getAlarmTotal());
            appendPlainCard(sb, "主从切换总次数", ov.getSwitchTotal());
            appendPlainCard(sb, "最近切换时间", fmt(ov.getLastSwitchTime()));
            appendPlainCard(sb, "Redis服务可用率(%)", ov.getServiceAvailableRate());
            appendPlainCard(sb, "主节点数", ov.getMasterCount());
            appendPlainCard(sb, "从节点数", ov.getReplicaCount());
            sb.append("</div>");
        }

        sb.append("<h2>二、指标趋势</h2>");
        List<ReportTrendDTO> trends = report.getTrendList();
        if (trends != null) {
            for (ReportTrendDTO trend : trends) {
                sb.append("<h3>").append(escape(trend.getMetricName()))
                        .append("(").append(escape(trend.getUnit())).append(")</h3>");
                sb.append(buildLineChartSvg(trend));
            }
        }

        sb.append("<h2>三、告警与主从切换明细</h2>");
        sb.append("<table><tr><th>事件类型</th><th>节点</th><th>内容</th><th>发生时间</th><th>状态</th></tr>");
        List<ReportEventDTO> events = report.getEventList();
        if (events != null) {
            for (ReportEventDTO event : events) {
                sb.append("<tr><td>").append(escape(event.getEventType())).append("</td>")
                        .append("<td>").append(escape(event.getNodeDisplay())).append("</td>")
                        .append("<td>").append(escape(event.getContent())).append("</td>")
                        .append("<td>").append(escape(fmt(event.getHappenTime()))).append("</td>")
                        .append("<td>").append(escape(event.getStatus())).append("</td></tr>");
            }
        }
        sb.append("</table>");

        sb.append("</body></html>");
        Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
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
     * 加载中文字体。
     * 1) 优先使用 classpath 内置 TTF（若项目自带且文件完整）；
     * 2) 其次使用操作系统常见中文字体（Windows/Linux）；
     * 3) 最后回退到 OpenPDF 自带的 STSong-Light/UniGB-UCS2-H。
     * 这样即使 classpath 中的字体文件缺失/被截断，也不会因 EOFException 导致整个导出任务失败。
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
     * 读取操作系统常见中文字体。仅用于本机部署兜底；
     * 生产环境建议在 classpath 放置完整的开源中文字体（如 Noto Sans SC）。
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
            throw new IOException("生成 PDF 失败: " + errorMessage(e), e);
        }
    }

    private Paragraph sectionHeading(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(12f);
        paragraph.setSpacingAfter(4f);
        return paragraph;
    }

    /**
     * 8 项运行报告概览卡片，与历史监控页卡片字段和顺序保持一致。
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
                integer(stats == null ? 0L : stats.getAlarmTotal()),
                "严重" + integer(stats == null ? 0L : stats.getSeverityCount())
                        + "｜警告" + integer(stats == null ? 0L : stats.getAlarmCount())
                        + "｜提示" + integer(stats == null ? 0L : stats.getInfoCount()),
                titleFont, valueFont, descFont);
        addOverviewCard(table, "主从切换次数",
                integer(ov == null ? 0L : ov.getSwitchTotal()),
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
     * 4 张趋势图，按 2×2 排列，与历史监控页监控指标统计一致。
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
     * 使用 PdfTemplate 绘制矢量折线图，避免额外引入图形库。
     */
    private PdfTemplate buildLineChart(PdfWriter writer, BaseFont baseFont,
                                       ReportTrendDTO trend, String reportType,
                                       Color lineColor) throws Exception {
        float width = 240f;
        float height = 130f;
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
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        int validCount = 0;
        for (int i = 0; i < count; i++) {
            Double value = values.get(i);
            if (value == null) {
                continue;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
            validCount++;
        }
        if (validCount < 2) {
            drawNoData(template, baseFont, width, height);
            return template;
        }
        if (min == max) {
            max = min + 1;
        } else if (min > 0) {
            // 与页面 ECharts 的 min: 0 保持一致
            min = 0;
        }

        float left = 30f;
        float right = width - 8f;
        float top = height - 10f;
        float bottom = 22f;

        // 网格 + 纵轴刻度
        template.setLineWidth(0.3f);
        template.setColorStroke(GRID_COLOR);
        for (int i = 0; i <= 4; i++) {
            float y = top - (top - bottom) * i / 4f;
            template.moveTo(left, y);
            template.lineTo(right, y);
            template.stroke();
        }
        template.setFontAndSize(baseFont, 6f);
        template.setColorFill(MUTED_COLOR);
        template.showTextAligned(Element.ALIGN_RIGHT, axisValue(max), left - 3, top - 2, 0);
        template.showTextAligned(Element.ALIGN_RIGHT, axisValue((max + min) / 2), left - 3,
                (top + bottom) / 2 - 2, 0);
        template.showTextAligned(Element.ALIGN_RIGHT, axisValue(min), left - 3, bottom - 2, 0);

        // 横轴首/中/尾时间
        int middle = count / 2;
        template.showTextAligned(Element.ALIGN_LEFT,
                shortTime(times.get(0), reportType), left, bottom - 9, 0);
        template.showTextAligned(Element.ALIGN_CENTER,
                shortTime(times.get(middle), reportType), (left + right) / 2, bottom - 9, 0);
        template.showTextAligned(Element.ALIGN_RIGHT,
                shortTime(times.get(count - 1), reportType), right, bottom - 9, 0);

        // 折线
        template.setColorStroke(lineColor);
        template.setLineWidth(1.3f);
        boolean started = false;
        for (int i = 0; i < count; i++) {
            Double value = values.get(i);
            if (value == null) {
                continue;
            }
            float x = left + (right - left) * i / (count - 1f);
            float y = bottom + (top - bottom) * (float) ((value - min) / (max - min));
            if (!started) {
                template.moveTo(x, y);
                started = true;
            } else {
                template.lineTo(x, y);
            }
        }
        if (started) {
            template.stroke();
        }
        return template;
    }

    private void drawNoData(PdfTemplate template, BaseFont baseFont, float width, float height) {
        template.setFontAndSize(baseFont, 7f);
        template.setColorFill(MUTED_COLOR);
        template.showTextAligned(Element.ALIGN_CENTER, "暂无数据", width / 2, height / 2, 0);
    }

    /**
     * 告警与主从切换统计表，列与历史监控页保持一致。
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
                    ? "--" : event.getHappenTime().format(FORMATTER), bodyFont, false);
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

    // ---------------------------------------------------------------- 报告格式化

    private String reportRange(String reportType) {
        LocalDate today = LocalDate.now();
        if ("周报".equals(reportType)) {
            return today.minusDays(6).format(DATE_FMT) + " 至 " + today.format(DATE_FMT);
        }
        return today.format(DATE_FMT);
    }

    private String percent(Object value) {
        return decimal(value, 1);
    }

    private String decimal(Object value, int scale) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return "--";
        }
        try {
            return new BigDecimal(String.valueOf(value)).setScale(scale, RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (Exception e) {
            return "--";
        }
    }

    private String integer(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return "--";
        }
        try {
            return String.format(Locale.US, "%,d",
                    new BigDecimal(String.valueOf(value)).longValue());
        } catch (Exception e) {
            return "--";
        }
    }

    private String withUnit(String value, String unit) {
        return "--".equals(value) ? value : value + unit;
    }

    private String timeSuffix(LocalDateTime time) {
        return time == null ? "" : "｜" + time.format(TIME_FMT);
    }

    private String timeText(LocalDateTime time) {
        return time == null ? "--" : time.format(TIME_FMT);
    }

    private long longValue(Long value) {
        return value == null ? 0L : value;
    }

    private String axisValue(double value) {
        if (Math.abs(value) >= 100) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private String shortTime(String timestamp, String reportType) {
        if (timestamp == null) {
            return "";
        }
        String text = timestamp.trim();
        if (text.length() >= 16) {
            return "周报".equals(reportType) ? text.substring(5, 16) : text.substring(11, 16);
        }
        return text;
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty()
                ? "--" : String.valueOf(value);
    }

    private String errorMessage(Exception e) {
        if (e == null) {
            return "未知错误";
        }
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }


    private void appendCard(StringBuilder sb, String name, Object avg, Object max, LocalDateTime maxTime) {
        sb.append("<div class=\"card\"><div>").append(escape(name)).append("</div>")
                .append("<div class=\"avg\">").append(nullSafe(avg)).append("</div>")
                .append("<div class=\"peak\">峰值 ").append(nullSafe(max))
                .append("　").append(escape(fmt(maxTime))).append("</div></div>");
    }

    private void appendPlainCard(StringBuilder sb, String name, Object value) {
        sb.append("<div class=\"card\"><div>").append(escape(name)).append("</div>")
                .append("<div class=\"avg\">").append(nullSafe(value)).append("</div></div>");
    }

    /**
     * 生成简易 SVG 折线图（无外部依赖）。
     */
    private String buildLineChartSvg(ReportTrendDTO trend) {
        List<String> times = trend.getTimestampList();
        List<Double> values = trend.getValueList();
        if (times == null || values == null || times.isEmpty() || values.isEmpty()) {
            return "<p>无数据</p>";
        }
        int width = 640;
        int height = 220;
        int padLeft = 50;
        int padBottom = 30;
        int padTop = 10;
        int padRight = 10;

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Double v : values) {
            if (v == null) {
                continue;
            }
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (min > max) {
            min = 0;
            max = 1;
        }
        if (min == max) {
            max = min + 1;
        }

        int count = Math.min(times.size(), values.size());
        double stepX = count > 1 ? (double) (width - padLeft - padRight) / (count - 1) : 0;

        StringBuilder points = new StringBuilder();
        for (int i = 0; i < count; i++) {
            Double v = values.get(i);
            if (v == null) {
                continue;
            }
            double x = padLeft + i * stepX;
            double y = padTop + (height - padTop - padBottom) * (1 - (v - min) / (max - min));
            if (points.length() > 0) {
                points.append(" ");
            }
            points.append(String.format("%.1f,%.1f", x, y));
        }

        StringBuilder svg = new StringBuilder();
        svg.append("<svg width=\"").append(width).append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 ").append(width).append(" ").append(height).append("\"")
                .append(" xmlns=\"http://www.w3.org/2000/svg\" style=\"background:#fafafa;border:1px solid #eee;\">");
        // 坐标轴
        svg.append("<line x1=\"").append(padLeft).append("\" y1=\"").append(height - padBottom)
                .append("\" x2=\"").append(width - padRight).append("\" y2=\"").append(height - padBottom)
                .append("\" stroke=\"#999\"/>");
        svg.append("<line x1=\"").append(padLeft).append("\" y1=\"").append(padTop)
                .append("\" x2=\"").append(padLeft).append("\" y2=\"").append(height - padBottom)
                .append("\" stroke=\"#999\"/>");
        // 最大值/最小值标注
        svg.append("<text x=\"").append(4).append("\" y=\"").append(padTop + 10)
                .append("\" font-size=\"10\" fill=\"#666\">").append(String.format("%.2f", max)).append("</text>");
        svg.append("<text x=\"").append(4).append("\" y=\"").append(height - padBottom)
                .append("\" font-size=\"10\" fill=\"#666\">").append(String.format("%.2f", min)).append("</text>");
        // 首尾时间标注
        svg.append("<text x=\"").append(padLeft).append("\" y=\"").append(height - 10)
                .append("\" font-size=\"10\" fill=\"#666\">").append(escape(times.get(0))).append("</text>");
        svg.append("<text x=\"").append(width - padRight - 110).append("\" y=\"").append(height - 10)
                .append("\" font-size=\"10\" fill=\"#666\">").append(escape(times.get(count - 1))).append("</text>");
        // 折线
        svg.append("<polyline points=\"").append(points).append("\" fill=\"none\" stroke=\"#d9534f\" stroke-width=\"1.5\"/>");
        svg.append("</svg>");
        return svg.toString();
    }

    // ---------------------------------------------------------------- 工具

    private String nullSafe(Object value) {
        return value == null ? "-" : value.toString();
    }

    private String fmt(LocalDateTime time) {
        return time == null ? "" : time.format(FORMATTER);
    }


    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
