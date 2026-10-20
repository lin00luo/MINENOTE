package com.example.minenote;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Query DeepSeek's official monetary balance API off the main thread. */
public final class AiBalanceClient {
    public interface Callback { void onResult(String result); }
    private AiBalanceClient() {}

    public static void queryDeepSeek(String apiKey, Callback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            String message;
            try {
                conn = (HttpURLConnection) new URL("https://api.deepseek.com/user/balance").openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                if (code == 401 || code == 403) {
                    message = "API Key 无效或无查询权限";
                } else if (code != 200) {
                    message = "余额查询失败 (HTTP " + code + ")";
                } else {
                    StringBuilder raw = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) raw.append(line);
                    }
                    JSONObject body = new JSONObject(raw.toString());
                    JSONArray infos = body.optJSONArray("balance_infos");
                    if (infos == null || infos.length() == 0) {
                        message = "未返回余额数据";
                    } else {
                        StringBuilder values = new StringBuilder();
                        for (int i = 0; i < infos.length(); i++) {
                            JSONObject item = infos.getJSONObject(i);
                            if (i > 0) values.append("  /  ");
                            String currency = item.optString("currency", "");
                            values.append("CNY".equals(currency) ? "¥" :
                                    "USD".equals(currency) ? "$" : currency + " ");
                            values.append(item.optString("total_balance", "--"));
                        }
                        message = values.toString();
                    }
                }
            } catch (Exception e) {
                message = "余额查询失败，请检查网络";
            } finally {
                if (conn != null) conn.disconnect();
            }
            callback.onResult(message);
        }, "DeepSeekBalance").start();
    }
}
