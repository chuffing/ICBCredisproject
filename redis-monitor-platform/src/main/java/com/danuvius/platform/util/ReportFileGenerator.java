package com.danuvius.platform.util;

import com.danuvius.platform.model.dto.monitor.ReportEventDTO;
import com.danuvius.platform.model.dto.monitor.ReportOverviewDTO;
import com.danuvius.platform.model.dto.monitor.ReportQueryResponse;
import com.danuvius.platform.model.dto.monitor.ReportTrendDTO;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报告文件生成器。
 * 导出内容与查询条件一致：概览统计、指标趋势图、告警与切换明细、节点可用率。
 */
@Slf4j
@Component
public class ReportFileGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static BaseFont CN_FONT;
    private static final Object FONT_LOCK = new Object();

    /**
     * 生成导出文件。
     * @return 生成的文件
     */
    public File generate(ReportQueryResponse report, String instanceId,
                         String reportType, String exportDir) throws IOException {
        File dir = new File(exportDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("创建导出目录失败: " + exportDir);
        }
        File file = new File(dir, "redis-report-" + instanceId + "-" + reportType + "-"
                + System.currentTimeMillis() + ".pdf");
        writePdf(file, report, instanceId, reportType);
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
            appendPlainCard(sb, "告警总次数", ov.getAlarmStatsDTO().getAlarmTotal());
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

    private BaseFont cnBaseFont() throws Exception {
        if (CN_FONT == null) {
            synchronized (FONT_LOCK) {
                if (CN_FONT == null) {
                    byte[] fontBytes = org.springframework.util.StreamUtils.copyToByteArray(
                            getClass().getResourceAsStream("/fonts/simhei.ttf"));
                    CN_FONT = BaseFont.createFont("simhei.ttf", BaseFont.IDENTITY_H,
                            BaseFont.EMBEDDED, BaseFont.CACHED, fontBytes, null);
                }
            }
        }
        return CN_FONT;
    }

    // ---------------------------------------------------------------- PDF
    private void writePdf(File file, ReportQueryResponse report, String instanceId,
                          String reportType) throws IOException {
        Document document = new Document();
        try (FileOutputStream out = new FileOutputStream(file)) {
            PdfWriter.getInstance(document, out);
            document.open();
            Font h1 = new Font(cnBaseFont(), 16, Font.BOLD);
            Font h2 = new Font(cnBaseFont(), 12, Font.BOLD);
            Font body = new Font(cnBaseFont(), 10, Font.NORMAL);


            document.add(new Paragraph("Redis 运行报告（" + reportType + "）", h1));
            document.add(new Paragraph("实例：" + instanceId + "　生成时间："
                    + LocalDateTime.now().format(FORMATTER)));

            ReportOverviewDTO ov = report.getOverview();
            document.add(new Paragraph("一、概览统计", h2));
            if (ov != null) {
                document.add(new Paragraph("CPU平均使用率(%)：" + nullSafe(ov.getCpuAvg())
                        + "　峰值：" + nullSafe(ov.getCpuMax()) + "　" + fmt(ov.getCpuMaxTim())));
                document.add(new Paragraph("Redis内存平均使用量(GB)：" + nullSafe(ov.getRedisMemAvg())
                        + "　峰值：" + nullSafe(ov.getRedisMemMax()) + "　" + fmt(ov.getRedisMemMaxTim())));
                document.add(new Paragraph("QPS平均值(次/秒)：" + nullSafe(ov.getQpsAvg())
                        + "　峰值：" + nullSafe(ov.getQpsMax()) + "　" + fmt(ov.getQpsMaxTim())));
                document.add(new Paragraph("平均延迟(ms)：" + nullSafe(ov.getLatencyAvg())
                        + "　峰值：" + nullSafe(ov.getLatencyMax()) + "　" + fmt(ov.getLatencyMaxTim())));
                document.add(new Paragraph("告警总次数：" + nullSafe(ov.getAlarmStatsDTO().getAlarmTotal())
                        + "　主从切换总次数：" + nullSafe(ov.getSwitchTotal())
                        + "　可用率(%)：" + nullSafe(ov.getServiceAvailableRate())
                        + "　主节点数：" + nullSafe(ov.getMasterCount())
                        + "　从节点数：" + nullSafe(ov.getReplicaCount())));
            }

            document.add(new Paragraph("二、指标趋势", h2));
            List<ReportTrendDTO> trends = report.getTrendList();
            if (trends != null) {
                for (ReportTrendDTO trend : trends) {
                    document.add(new Paragraph(trend.getMetricName() + "(" + trend.getUnit() + ")"));
                    // 如需趋势图，用 OpenPDF 的 LineChart/绘图 API，或嵌入预生成图片；
                    // 文本先输出首尾值，避免依赖图形库
                    List<Double> vals = trend.getValueList();
                    if (vals != null && !vals.isEmpty()) {
                        document.add(new Paragraph("  首值：" + vals.get(0)
                                + "　末值：" + vals.get(vals.size() - 1)));
                    }
                }
            }

            document.add(new Paragraph("三、告警与主从切换明细", h2));
            List<ReportEventDTO> events = report.getEventList();
            if (events != null) {
                for (ReportEventDTO event : events) {
                    document.add(new Paragraph(
                            event.getEventType() + " | " + event.getNodeDisplay() + " | "
                                    + event.getContent() + " | " + fmt(event.getHappenTime())
                                    + " | " + event.getStatus()));
                }
            }
            document.close();
        } catch (Exception e) {
            throw new IOException("生成 PDF 失败: " + e.getMessage(), e);
        }
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
