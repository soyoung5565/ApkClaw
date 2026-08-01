package com.apk.claw.android.tool.impl;

import com.apk.claw.android.ClawApplication;
import com.apk.claw.android.R;
import com.apk.claw.android.tool.BaseTool;
import com.apk.claw.android.tool.ToolParameter;
import com.apk.claw.android.tool.ToolResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * DumpFullUiTool — back-up for GetScreenInfoTool when the a11y tree is empty
 * (notably inside WebViews: Taobao/Tmall shan'gou, JD miaosong, and any
 * page rendered as H5 inside a WebView). Uses `uiautomator dump` to read
 * the raw View hierarchy straight from the device — this works where a11y
 * does not, because uiautomator talks to ViewServer rather than to the
 * accessibility service.
 *
 * Returns the full UI XML inline (not a path) so the LLM can read it in
 * one round trip. The XML is the same format `adb shell uiautomator dump`
 * produces, including WebView content nodes.
 *
 * Use this when get_screen_info returns `__SYSTEM_DIALOG_BLOCKED__` or an
 * empty tree and the screen is clearly not blank (e.g. a WebView page).
 */
public class DumpFullUiTool extends BaseTool {

    private static final String DEVICE_PATH = "/sdcard/_phone_controller_ui.xml";
    /** Hard cap so a runaway dump does not blow the LLM context window. */
    private static final int MAX_XML_BYTES = 200_000;

    @Override
    public String getName() {
        return "dump_full_ui";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_get_screen_info);
    }

    @Override
    public String getDescriptionEN() {
        return "Dump the current screen's full UI hierarchy as raw XML using `uiautomator dump`. "
            + "Use this when get_screen_info returns an empty tree but the screen is clearly not blank "
            + "(typical: WebView pages such as Taobao shan'gou / JD miaosong). "
            + "Returns the XML inline. Each node has `text`, `content-desc`, `resource-id`, `class`, "
            + "`bounds`, `clickable`, and other attributes. "
            + "Coordinates are in the same coordinate space as tap() / swipe().";
    }

    @Override
    public String getDescriptionCN() {
        return "用 `uiautomator dump` 抓取当前屏幕的完整 UI 层级（XML 原文）。"
            + "当 get_screen_info 返回空（特别是 WebView 页面如淘宝闪购、京东秒送）时使用这个。"
            + "返回 XML 内联文本。节点包含 text / content-desc / resource-id / class / bounds / clickable 等属性。"
            + "坐标与 tap / swipe 同一坐标系。";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.emptyList();
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        Process p = null;
        File dumpFile = new File(ClawApplication.Companion.getInstance().getCacheDir(), "_dump_full_ui.xml");
        try {
            // Run uiautomator dump; capture to a known path on the device, then pull.
            ProcessBuilder pb = new ProcessBuilder(
                "uiautomator", "dump", DEVICE_PATH
            );
            // Run as the app's UID — on Android 11+ this works via the shell.
            // Use shell so we don't need root.
            pb.command("sh", "-c",
                "uiautomator dump " + DEVICE_PATH + " 2>&1; " +
                "cat " + DEVICE_PATH + " > " + dumpFile.getAbsolutePath() + " 2>/dev/null"
            );
            pb.redirectErrorStream(true);
            p = pb.start();
            // uiautomator dump can take 2-5s on a slow device; allow 10s.
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return ToolResult.error("uiautomator dump timed out after 10s");
            }

            if (!dumpFile.exists() || dumpFile.length() == 0) {
                return ToolResult.error("uiautomator dump produced no file. The a11y service may not be running, or the device is in transition.");
            }

            // Read the XML inline. Truncate if it exceeds the cap so the
            // LLM context does not blow up.
            StringBuilder sb = new StringBuilder((int) Math.min(dumpFile.length(), MAX_XML_BYTES + 1024));
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(dumpFile), StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int n;
                int read = 0;
                while ((n = br.read(buf)) > 0) {
                    if (read + n > MAX_XML_BYTES) {
                        int allowed = MAX_XML_BYTES - read;
                        if (allowed > 0) {
                            sb.append(buf, 0, allowed);
                        }
                        sb.append("\n\n[... truncated: UI dump exceeded ")
                          .append(MAX_XML_BYTES)
                          .append(" bytes; the rest is omitted to fit the LLM context window. "
                                  + "Re-run with a smaller scope if you need the rest.]");
                        break;
                    }
                    sb.append(buf, 0, n);
                    read += n;
                }
            }
            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("dump_full_ui failed: " + e.getMessage());
        } finally {
            if (p != null && p.isAlive()) p.destroyForcibly();
            if (dumpFile.exists()) {
                // noinspection ResultOfMethodCallIgnored
                dumpFile.delete();
            }
        }
    }
}
