package com.example.minenote;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AIClient {

    public interface Callback {
        void onSuccess(String result);
        void onError(String error);
    }

    public static class QuizQuestion {
        public String type;
        public String question;
        public String[] options;
        public String answer;
        public String explanation;
    }

    public interface QuizCallback {
        void onSuccess(QuizQuestion[] questions);
        void onError(String error);
    }

    private final Context context;

    public AIClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 根据笔记生成结构化复习题。
     */
    public void generateQuiz(
            String noteContent,
            QuizCallback callback
    ) {

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                SharedPreferences preferences =
                        context.getSharedPreferences(
                                "ai_settings",
                                Context.MODE_PRIVATE
                        );

                String apiKey =
                        preferences.getString("api_key", "");

                String baseUrl =
                        preferences.getString(
                                "base_url",
                                "https://api.openai.com/v1"
                        );

                String model =
                        preferences.getString("model", "");

                if (apiKey.trim().isEmpty()) {
                    callback.onError("还没有设置 API Key");
                    return;
                }

                if (model.trim().isEmpty()) {
                    callback.onError("还没有设置模型");
                    return;
                }

                if (baseUrl.trim().isEmpty()) {
                    callback.onError("还没有设置 Base URL");
                    return;
                }

                if (noteContent == null ||
                        noteContent.trim().isEmpty()) {
                    callback.onError("这篇笔记还是空的");
                    return;
                }

                baseUrl = removeTrailingSlash(baseUrl.trim());

                URL url =
                        new URL(
                                baseUrl +
                                        "/chat/completions"
                        );

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );

                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + apiKey
                );

                String prompt =
                        "请根据下面的笔记生成 5 道复习题。\n\n" +
                                "要求：\n" +
                                "1. 题目必须严格根据笔记内容。\n" +
                                "2. 难度从基础到稍有思考逐渐增加。\n" +
                                "3. 可以使用 choice（选择题）、true_false（判断题）、short_answer（简答题）。\n" +
                                "4. choice 必须提供 4 个 options，answer 填正确选项的完整文字。\n" +
                                "5. true_false 的 options 固定为“正确”和“错误”，answer 必须是其中一个。\n" +
                                "6. short_answer 的 options 必须是空数组，answer 填参考答案。\n" +
                                "7. 每题提供简短 explanation。\n" +
                                "8. 只返回 JSON 数组，不要 Markdown，不要代码块，不要额外文字。\n\n" +
                                "JSON 格式必须严格类似：\n" +
                                "[{\"type\":\"choice\",\"question\":\"问题\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":\"A\",\"explanation\":\"解释\"}]\n\n" +
                                "笔记内容：\n" +
                                noteContent;

                JSONObject body =
                        new JSONObject();

                body.put("model", model);

                JSONArray messages =
                        new JSONArray();

                JSONObject systemMessage =
                        new JSONObject();

                systemMessage.put(
                        "role",
                        "system"
                );

                systemMessage.put(
                        "content",
                        "你是 MINENOTE 的复习题生成助手。"
                );

                JSONObject userMessage =
                        new JSONObject();

                userMessage.put(
                        "role",
                        "user"
                );

                userMessage.put(
                        "content",
                        prompt
                );

                messages.put(systemMessage);
                messages.put(userMessage);

                body.put("messages", messages);
                body.put("temperature", 0.4);

                byte[] data =
                        body.toString()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );

                try (OutputStream outputStream =
                             connection.getOutputStream()) {

                    outputStream.write(data);
                    outputStream.flush();
                }

                int responseCode =
                        connection.getResponseCode();

                InputStream inputStream;

                if (responseCode >= 200 &&
                        responseCode < 300) {

                    inputStream =
                            connection.getInputStream();

                } else {

                    inputStream =
                            connection.getErrorStream();
                }

                String response =
                        readStream(inputStream);

                if (responseCode < 200 ||
                        responseCode >= 300) {

                    callback.onError(
                            parseError(response)
                    );
                    return;
                }

                JSONObject result =
                        new JSONObject(response);

                recordTokenUsage(result);

                JSONArray choices =
                        result.getJSONArray("choices");

                if (choices.length() == 0) {
                    callback.onError("AI 没有返回结果");
                    return;
                }

                JSONObject message =
                        choices
                                .getJSONObject(0)
                                .getJSONObject("message");

                String content =
                        message.optString(
                                "content",
                                ""
                        ).trim();

                QuizQuestion[] questions =
                        parseQuiz(content);

                if (questions.length == 0) {
                    callback.onError(
                            "AI 返回的复习题格式无法识别"
                    );
                    return;
                }

                callback.onSuccess(questions);

            } catch (Exception e) {

                callback.onError(
                        getFriendlyError(e)
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }

    /**
     * 默认：综合分析笔记。
     * 保留原来的调用方式，避免旧代码报错。
     */
    public void analyzeNote(
            String noteContent,
            Callback callback
    ) {
        analyzeNote(
                noteContent,
                "分析这篇笔记",
                callback
        );
    }

    /**
     * 根据不同任务分析笔记。
     *
     * task 可以是：
     * - 总结这篇笔记
     * - 提取知识点
     * - 提取待办事项
     * - 润色这篇笔记
     * - 生成复习题
     * - 自定义提问
     */
    public void analyzeNote(
            String noteContent,
            String task,
            Callback callback
    ) {

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                // =========================
                // 读取 AI 设置
                // =========================

                SharedPreferences preferences =
                        context.getSharedPreferences(
                                "ai_settings",
                                Context.MODE_PRIVATE
                        );

                String apiKey =
                        preferences.getString(
                                "api_key",
                                ""
                        );

                String baseUrl =
                        preferences.getString(
                                "base_url",
                                "https://api.openai.com/v1"
                        );

                String model =
                        preferences.getString(
                                "model",
                                ""
                        );

                // =========================
                // 检查设置
                // =========================

                if (apiKey.trim().isEmpty()) {
                    callback.onError("还没有设置 API Key");
                    return;
                }

                if (model.trim().isEmpty()) {
                    callback.onError("还没有设置模型");
                    return;
                }

                if (baseUrl.trim().isEmpty()) {
                    callback.onError("还没有设置 Base URL");
                    return;
                }

                // =========================
                // 检查笔记内容
                // =========================

                if (noteContent == null ||
                        noteContent.trim().isEmpty()) {

                    callback.onError("这篇笔记还是空的，暂时没有内容可以分析");
                    return;
                }

                // =========================
                // 处理 Base URL
                // =========================

                baseUrl =
                        removeTrailingSlash(
                                baseUrl.trim()
                        );

                String endpoint =
                        baseUrl +
                                "/chat/completions";

                // =========================
                // 创建连接
                // =========================

                URL url =
                        new URL(endpoint);

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setRequestMethod("POST");

                connection.setConnectTimeout(
                        15000
                );

                connection.setReadTimeout(
                        60000
                );

                connection.setDoOutput(
                        true
                );

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );

                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + apiKey
                );

                // =========================
                // 创建 Prompt
                // =========================

                String prompt =
                        buildPrompt(
                                noteContent,
                                task
                        );

                // =========================
                // 构造 JSON
                // =========================

                JSONObject body =
                        new JSONObject();

                body.put(
                        "model",
                        model
                );

                JSONArray messages =
                        new JSONArray();

                JSONObject systemMessage =
                        new JSONObject();

                systemMessage.put(
                        "role",
                        "system"
                );

                systemMessage.put(
                        "content",
                        "你是 MINENOTE 的 AI 笔记助手。"
                                + "你的任务是帮助用户整理、理解和复习自己的笔记。"
                                + "回答应该清晰、准确、实用。"
                                + "不要凭空编造笔记中不存在的事实。"
                );

                JSONObject userMessage =
                        new JSONObject();

                userMessage.put(
                        "role",
                        "user"
                );

                userMessage.put(
                        "content",
                        prompt
                );

                messages.put(
                        systemMessage
                );

                messages.put(
                        userMessage
                );

                body.put(
                        "messages",
                        messages
                );

                body.put(
                        "temperature",
                        0.7
                );

                // =========================
                // 发送请求
                // =========================

                byte[] data =
                        body.toString()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );

                try (OutputStream outputStream =
                             connection.getOutputStream()) {

                    outputStream.write(data);
                    outputStream.flush();
                }

                // =========================
                // 获取 HTTP 状态码
                // =========================

                int responseCode =
                        connection.getResponseCode();

                InputStream inputStream;

                if (responseCode >= 200 &&
                        responseCode < 300) {

                    inputStream =
                            connection.getInputStream();

                } else {

                    inputStream =
                            connection.getErrorStream();
                }

                String response =
                        readStream(
                                inputStream
                        );

                // =========================
                // 处理错误
                // =========================

                if (responseCode < 200 ||
                        responseCode >= 300) {

                    callback.onError(
                            parseError(response)
                    );

                    return;
                }

                // =========================
                // 解析 AI 返回结果
                // =========================

                JSONObject result =
                        new JSONObject(response);

                recordTokenUsage(result);

                JSONArray choices =
                        result.getJSONArray(
                                "choices"
                        );

                if (choices.length() == 0) {

                    callback.onError(
                            "AI 没有返回结果"
                    );

                    return;
                }

                JSONObject firstChoice =
                        choices.getJSONObject(0);

                JSONObject message =
                        firstChoice.getJSONObject(
                                "message"
                        );

                String content =
                        message.optString(
                                "content",
                                ""
                        );

                if (content.trim().isEmpty()) {

                    callback.onError(
                            "AI 返回了空内容"
                    );

                    return;
                }

                callback.onSuccess(
                        content
                );

            } catch (Exception e) {

                callback.onError(
                        getFriendlyError(e)
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }

    // =========================
    // 根据任务生成 Prompt
    // =========================

    private String buildPrompt(
            String noteContent,
            String task
    ) {

        String safeTask =
                task == null || task.trim().isEmpty()
                        ? "分析这篇笔记"
                        : task.trim();

        if ("总结这篇笔记".equals(safeTask)) {

            return
                    "请总结下面这篇笔记。\n\n" +

                            "要求：\n" +
                            "1. 先用一小段话概括全文。\n" +
                            "2. 再列出 3～5 个最重要的关键点。\n" +
                            "3. 不要加入笔记中没有的信息。\n" +
                            "4. 使用清晰的分点格式。\n\n" +

                            "笔记内容：\n" +
                            noteContent;
        }

        if ("提取知识点".equals(safeTask)) {

            return
                    "请从下面这篇笔记中提取值得学习和记忆的知识点。\n\n" +

                            "要求：\n" +
                            "1. 按重要程度整理。\n" +
                            "2. 每个知识点尽量简洁。\n" +
                            "3. 对比较难理解的知识点补充一句解释。\n" +
                            "4. 只使用笔记中能够支持的信息，不要凭空扩展。\n\n" +

                            "笔记内容：\n" +
                            noteContent;
        }

        if ("提取待办事项".equals(safeTask)) {

            return
                    "请从下面这篇笔记中提取所有明确或明显的待办事项。\n\n" +

                            "要求：\n" +
                            "1. 使用复选框风格列出，例如：☐ 完成 Java 作业。\n" +
                            "2. 如果没有明确的待办事项，请直接说明“没有发现明确的待办事项”。\n" +
                            "3. 不要把普通知识点强行变成待办事项。\n\n" +

                            "笔记内容：\n" +
                            noteContent;
        }

        if ("润色这篇笔记".equals(safeTask)) {

            return
                    "请润色下面这篇笔记。\n\n" +

                            "要求：\n" +
                            "1. 保留原意和事实。\n" +
                            "2. 修正明显的错别字、病句和表达问题。\n" +
                            "3. 让结构更加清晰。\n" +
                            "4. 不要无故增加新的观点。\n" +
                            "5. 直接给出润色后的完整内容，不要写分析过程。\n\n" +

                            "原笔记：\n" +
                            noteContent;
        }

        if ("生成复习题".equals(safeTask)) {

            return
                    "请根据下面这篇笔记生成复习题。\n\n" +

                            "要求：\n" +
                            "1. 生成 5 道题。\n" +
                            "2. 难度从基础到稍有思考逐渐增加。\n" +
                            "3. 可以包含选择题、判断题和简答题。\n" +
                            "4. 每道题后面给出参考答案。\n" +
                            "5. 答案必须能够从笔记内容中得到支持。\n\n" +

                            "笔记内容：\n" +
                            noteContent;
        }

        if ("自定义提问".equals(safeTask)) {

            return
                    "用户希望针对下面这篇笔记进行提问。\n\n" +

                            "请根据笔记内容回答用户的问题。\n" +
                            "如果笔记中没有足够的信息，请明确说明，不要编造。\n\n" +

                            "笔记内容：\n" +
                            noteContent;
        }

        return
                "请根据下面这篇笔记完成用户要求的任务。\n\n" +

                        "用户要求：\n" +
                        safeTask +
                        "\n\n" +

                        "笔记内容：\n" +
                        noteContent;
    }

    // =========================
    // 解析 AI 返回的复习题 JSON
    // =========================

    private QuizQuestion[] parseQuiz(
            String content
    ) throws Exception {

        String jsonText =
                content.trim();

        if (jsonText.startsWith("```")) {

            int firstNewLine =
                    jsonText.indexOf("\n");

            if (firstNewLine >= 0) {
                jsonText =
                        jsonText.substring(
                                firstNewLine + 1
                        );
            }

            int lastFence =
                    jsonText.lastIndexOf("```");

            if (lastFence >= 0) {
                jsonText =
                        jsonText.substring(
                                0,
                                lastFence
                        );
            }

            jsonText = jsonText.trim();
        }

        int start =
                jsonText.indexOf("[");

        int end =
                jsonText.lastIndexOf("]");

        if (start < 0 || end <= start) {
            throw new Exception(
                    "没有找到复习题 JSON 数组"
            );
        }

        jsonText =
                jsonText.substring(
                        start,
                        end + 1
                );

        JSONArray array =
                new JSONArray(jsonText);

        java.util.ArrayList<QuizQuestion> list =
                new java.util.ArrayList<>();

        for (int i = 0; i < array.length(); i++) {

            JSONObject item =
                    array.getJSONObject(i);

            QuizQuestion question =
                    new QuizQuestion();

            question.type =
                    item.optString(
                            "type",
                            "short_answer"
                    );

            question.question =
                    item.optString(
                            "question",
                            ""
                    );

            JSONArray optionsArray =
                    item.optJSONArray("options");

            if (optionsArray == null) {

                question.options =
                        new String[0];

            } else {

                question.options =
                        new String[
                                optionsArray.length()
                                ];

                for (int j = 0;
                     j < optionsArray.length();
                     j++) {

                    question.options[j] =
                            optionsArray.optString(
                                    j,
                                    ""
                            );
                }
            }

            question.answer =
                    item.optString(
                            "answer",
                            ""
                    );

            question.explanation =
                    item.optString(
                            "explanation",
                            ""
                    );

            if (!question.question
                    .trim()
                    .isEmpty()) {

                list.add(question);
            }
        }

        return list.toArray(
                new QuizQuestion[0]
        );
    }

    // =========================
    // 删除 URL 最后的 /
    // =========================

    // =========================
    // 统计 API 返回的 Token 使用量
    // =========================

    private void recordTokenUsage(JSONObject result) {

        try {
            JSONObject usage = result.optJSONObject("usage");

            if (usage == null) {
                return;
            }

            long promptTokens = usage.optLong("prompt_tokens", 0);
            long completionTokens = usage.optLong("completion_tokens", 0);
            long totalTokens = usage.optLong(
                    "total_tokens",
                    promptTokens + completionTokens
            );

            if (totalTokens <= 0) {
                return;
            }

            android.content.SharedPreferences preferences =
                    context.getSharedPreferences(
                            "token_stats",
                            Context.MODE_PRIVATE
                    );

            long oldPrompt = preferences.getLong(
                    "prompt_tokens", 0
            );

            long oldCompletion = preferences.getLong(
                    "completion_tokens", 0
            );

            long oldTotal = preferences.getLong(
                    "total_tokens", 0
            );

            SharedPreferences aiSettings = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE);
            String provider = aiSettings.getString("provider", "OpenAI");
            String apiKey = aiSettings.getString("api_key", "");
            preferences.edit()
                    .putString("last_account", provider + ":" + apiKey.hashCode())
                    .putLong("last_prompt_tokens", promptTokens)
                    .putLong("last_completion_tokens", completionTokens)
                    .putLong(
                            "prompt_tokens",
                            oldPrompt + promptTokens
                    )
                    .putLong(
                            "completion_tokens",
                            oldCompletion + completionTokens
                    )
                    .putLong(
                            "total_tokens",
                            oldTotal + totalTokens
                    )
                    .apply();

        } catch (Exception ignored) {
            // 某些兼容 API 不返回 usage，不影响正常 AI 功能。
        }
    }

    private String removeTrailingSlash(
            String url
    ) {

        while (url.endsWith("/")) {

            url = url.substring(
                    0,
                    url.length() - 1
            );
        }

        return url;
    }

    // =========================
    // 读取服务器响应
    // =========================

    private String readStream(
            InputStream inputStream
    ) throws Exception {

        if (inputStream == null) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                inputStream,
                                StandardCharsets.UTF_8
                        )
                );

        String line;

        while (
                (line = reader.readLine()) != null
        ) {

            result.append(line);
        }

        reader.close();

        return result.toString();
    }

    // =========================
    // 解析 API 错误
    // =========================

    private String parseError(
            String response
    ) {

        try {

            JSONObject json =
                    new JSONObject(response);

            if (json.has("error")) {

                JSONObject error =
                        json.getJSONObject(
                                "error"
                        );

                if (error.has("message")) {

                    return error.getString(
                            "message"
                    );
                }
            }

        } catch (Exception ignored) {
        }

        if (response == null ||
                response.trim().isEmpty()) {

            return "AI 请求失败，请检查网络和 API 设置";
        }

        return "AI 请求失败：\n" +
                response;
    }

    // =========================
    // 转换常见网络错误
    // =========================

    private String getFriendlyError(
            Exception e
    ) {

        if (e instanceof
                java.net.UnknownHostException) {

            return "无法连接服务器，请检查网络或 Base URL";
        }

        if (e instanceof
                java.net.SocketTimeoutException) {

            return "AI 请求超时，请稍后再试";
        }

        if (e instanceof
                java.net.ConnectException) {

            return "无法连接 AI 服务器，请检查网络";
        }

        return "AI 请求失败：" +
                e.getMessage();
    }
}
