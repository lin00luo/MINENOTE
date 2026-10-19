package com.example.minenote;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NoteDetailActivity extends AppCompatActivity {

    private EditText titleEditText;
    private EditText contentEditText;

    private AppDatabase database;
    private NoteDao noteDao;
    private FolderDao folderDao;

    private Note currentNote;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private Runnable saveRunnable;

    private boolean isLoadingNote = false;

    // AI 复习题状态
    private AIClient.QuizQuestion[] quizQuestions;
    private int quizIndex = 0;
    private int quizCorrectCount = 0;
    private int quizAnsweredCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_note_detail);

        titleEditText = findViewById(R.id.etDetailTitle);
        contentEditText = findViewById(R.id.etDetailContent);

        TextView btnBack = findViewById(R.id.btnBack);
        TextView btnMore = findViewById(R.id.btnMore);

        database = Room.databaseBuilder(
                        getApplicationContext(),
                        AppDatabase.class,
                        "minenote_database"
                )
                .addMigrations(
                        AppDatabaseMigration.MIGRATION_1_2,
                        AppDatabaseMigrationV3.MIGRATION_2_3,
                        AppDatabaseMigrationV4.MIGRATION_3_4
                )
                .build();

        noteDao = database.noteDao();
        folderDao = database.folderDao();

        int noteId = getIntent().getIntExtra("note_id", -1);

        if (noteId == -1) {
            Toast.makeText(this, "笔记不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadNote(noteId);

        btnBack.setOnClickListener(v -> {
            saveNoteImmediately();
            finish();
        });

        btnMore.setOnClickListener(v -> showMoreMenu());

        titleEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                if (!isLoadingNote) {
                    scheduleAutoSave();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        contentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                if (!isLoadingNote) {
                    scheduleAutoSave();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void loadNote(int noteId) {
        new Thread(() -> {
            Note note = noteDao.getNoteById(noteId);

            runOnUiThread(() -> {
                if (note == null) {
                    Toast.makeText(
                            NoteDetailActivity.this,
                            "笔记不存在",
                            Toast.LENGTH_SHORT
                    ).show();
                    finish();
                    return;
                }

                currentNote = note;

                isLoadingNote = true;
                titleEditText.setText(note.getTitle());
                contentEditText.setText(note.getContent());
                isLoadingNote = false;
            });
        }).start();
    }

    private void showMoreMenu() {
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setPadding(dp(6), dp(6), dp(6), dp(6));
        menuLayout.setBackground(createMenuBackground());

        TextView aiItem = createMoreMenuItem("AI 分析", "✦");
        TextView moveItem = createMoreMenuItem("移动", "↗");
        TextView deleteItem = createMoreMenuItem("删除", "⌫");

        menuLayout.addView(aiItem);
        menuLayout.addView(moveItem);
        menuLayout.addView(deleteItem);

        PopupWindow popupWindow = new PopupWindow(
                menuLayout,
                dp(190),
                dp(174),
                true
        );

        popupWindow.setBackgroundDrawable(createMenuBackground());
        popupWindow.setElevation(dp(10));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);

        aiItem.setOnClickListener(v -> {
            popupWindow.dismiss();
            showAIAssistant();
        });

        moveItem.setOnClickListener(v -> {
            popupWindow.dismiss();
            showFolderDialog();
        });

        deleteItem.setOnClickListener(v -> {
            popupWindow.dismiss();
            confirmDelete();
        });

        TextView btnMore = findViewById(R.id.btnMore);

        popupWindow.showAsDropDown(
                btnMore,
                -dp(150),
                dp(6)
        );
    }

    private TextView createMoreMenuItem(
            String text,
            String icon
    ) {
        TextView item = new TextView(this);

        item.setText(icon + "    " + text);
        item.setTextSize(15);
        item.setTextColor(Color.rgb(35, 35, 35));
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(16), 0, dp(12), 0);
        item.setBackground(createMenuItemBackground());

        item.setMinHeight(dp(50));

        return item;
    }

    private GradientDrawable createMenuBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(1, Color.rgb(235, 235, 235));
        return drawable;
    }

    private GradientDrawable createMenuItemBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private void showAIAssistant() {
        String content = contentEditText.getText().toString().trim();

        if (content.isEmpty()) {
            Toast.makeText(
                    this,
                    "这篇笔记还没有内容",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(8));

        TextView subtitle = new TextView(this);
        subtitle.setText("选择你想让 AI 帮你完成的操作");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.rgb(120, 120, 120));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(32)));

        addAIOption(root, "总结", "快速整理这篇笔记的核心内容", "✦", () ->
                analyzeWithAI("总结这篇笔记"));
        addAIOption(root, "知识点", "提取值得记忆的重点内容", "◇", () ->
                analyzeWithAI("提取知识点"));
        addAIOption(root, "待办事项", "找出笔记中的任务和行动项", "✓", () ->
                analyzeWithAI("提取待办事项"));
        addAIOption(root, "润色", "让文字表达更加清晰自然", "Aa", () ->
                analyzeWithAI("润色这篇笔记"));
        addAIOption(root, "生成复习题", "根据笔记内容生成练习题", "?", this::startQuiz);
        addAIOption(root, "自定义提问", "针对这篇笔记向 AI 提出问题", "…", this::showCustomAIQuestion);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("AI 助手")
                .setView(root)
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(createDialogBackground());
            dialog.getWindow().setDimAmount(0.28f);
        });

        dialog.show();
    }

    private void addAIOption(
            LinearLayout parent,
            String title,
            String description,
            String icon,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(createOptionBackground());
        row.setClickable(true);
        row.setFocusable(true);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(16);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTextColor(Color.rgb(107, 91, 0));
        iconView.setBackground(createIconBackground());
        row.addView(iconView, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setPadding(dp(12), 0, dp(4), 0);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTextColor(Color.rgb(32, 33, 36));

        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextSize(12);
        descView.setTextColor(Color.rgb(120, 120, 120));
        descView.setMaxLines(1);

        textBox.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        textBox.addView(descView, new LinearLayout.LayoutParams(-1, -2));

        row.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(24);
        arrow.setTextColor(Color.rgb(160, 160, 160));
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(40)));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(58));
        rowParams.bottomMargin = dp(5);
        parent.addView(row, rowParams);

        row.setOnClickListener(v -> action.run());
    }

    private GradientDrawable createOptionBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(250, 250, 250));
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private GradientDrawable createIconBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(255, 248, 225));
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private GradientDrawable createDialogBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(22));
        return drawable;
    }

    private void showCustomAIQuestion() {
        final EditText input = new EditText(this);

        input.setHint(
                "例如：帮我解释这篇笔记中最难的部分"
        );

        input.setSingleLine(false);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);

        int padding = dp(20);

        input.setPadding(
                padding,
                padding,
                padding,
                padding
        );

        new AlertDialog.Builder(this)
                .setTitle("问 AI")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("提问", (dialog, which) -> {
                    String question =
                            input.getText().toString().trim();

                    if (question.isEmpty()) {
                        Toast.makeText(
                                this,
                                "请输入问题",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    analyzeCustomQuestion(question);
                })
                .show();
    }

    private void analyzeWithAI(String task) {
        String content =
                contentEditText.getText().toString().trim();

        if (content.isEmpty()) {
            Toast.makeText(
                    this,
                    "这篇笔记还没有内容",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        AlertDialog loadingDialog =
                new AlertDialog.Builder(this)
                        .setTitle(task)
                        .setMessage(
                                "正在请求 AI，请稍候……"
                        )
                        .setCancelable(false)
                        .create();

        loadingDialog.show();

        AIClient aiClient =
                new AIClient(this);

        aiClient.analyzeNote(
                content,
                task,
                new AIClient.Callback() {
                    @Override
                    public void onSuccess(String result) {
                        runOnUiThread(() -> {
                            if (loadingDialog.isShowing()) {
                                loadingDialog.dismiss();
                            }

                            showAIResult(
                                    task,
                                    result
                            );
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            if (loadingDialog.isShowing()) {
                                loadingDialog.dismiss();
                            }

                            showAIError(error);
                        });
                    }
                }
        );
    }

    private void analyzeCustomQuestion(
            String question
    ) {
        String content =
                contentEditText.getText().toString().trim();

        if (content.isEmpty()) {
            Toast.makeText(
                    this,
                    "这篇笔记还没有内容",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        AlertDialog loadingDialog =
                new AlertDialog.Builder(this)
                        .setTitle("AI 问答")
                        .setMessage(
                                "正在思考，请稍候……"
                        )
                        .setCancelable(false)
                        .create();

        loadingDialog.show();

        AIClient aiClient =
                new AIClient(this);

        String task =
                "用户的问题是：\n" +
                        question +
                        "\n\n" +
                        "请严格结合这篇笔记回答问题。" +
                        "如果笔记中没有足够的信息，请明确说明，" +
                        "不要编造笔记中不存在的内容。";

        aiClient.analyzeNote(
                content,
                task,
                new AIClient.Callback() {
                    @Override
                    public void onSuccess(String result) {
                        runOnUiThread(() -> {
                            if (loadingDialog.isShowing()) {
                                loadingDialog.dismiss();
                            }

                            showAIResult(
                                    "自定义提问",
                                    result
                            );
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            if (loadingDialog.isShowing()) {
                                loadingDialog.dismiss();
                            }

                            showAIError(error);
                        });
                    }
                }
        );
    }

    // ==================== AI 复习题 ====================

    private void startQuiz() {
        String content =
                contentEditText.getText().toString().trim();

        if (content.isEmpty()) {
            Toast.makeText(
                    this,
                    "这篇笔记还没有内容",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        AlertDialog loadingDialog =
                new AlertDialog.Builder(this)
                        .setTitle("生成复习题")
                        .setMessage(
                                "AI 正在根据这篇笔记生成 5 道题，请稍候……"
                        )
                        .setCancelable(false)
                        .create();

        loadingDialog.show();

        AIClient aiClient =
                new AIClient(this);

        aiClient.generateQuiz(
                content,
                new AIClient.QuizCallback() {
                    @Override
                    public void onSuccess(
                            AIClient.QuizQuestion[] questions
                    ) {
                        runOnUiThread(() -> {
                            if (loadingDialog.isShowing()) {
                                loadingDialog.dismiss();
                            }

                            if (questions == null ||
                                    questions.length == 0) {
                                showAIError(
                                        "AI 没有生成有效的复习题。"
                                );
                                return;
                            }

                            quizQuestions = questions;
                            quizIndex = 0;
                            quizCorrectCount = 0;
                            quizAnsweredCount = 0;

                            showQuizQuestion();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            if (loadingDialog.isShowing()) {
                                loadingDialog.dismiss();
                            }

                            showAIError(error);
                        });
                    }
                }
        );
    }

    private void showQuizQuestion() {
        if (quizQuestions == null ||
                quizIndex >= quizQuestions.length) {
            showQuizResult();
            return;
        }

        AIClient.QuizQuestion question =
                quizQuestions[quizIndex];

        if (question == null) {
            quizIndex++;
            showQuizQuestion();
            return;
        }

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        int padding = dp(20);

        root.setPadding(
                padding,
                dp(8),
                padding,
                dp(4)
        );

        TextView progress =
                new TextView(this);

        progress.setText(
                "第 " +
                        (quizIndex + 1) +
                        " / " +
                        quizQuestions.length +
                        " 题"
        );

        progress.setTextSize(14);
        progress.setTextColor(
                Color.rgb(120, 120, 120)
        );

        root.addView(
                progress,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        TextView questionText =
                new TextView(this);

        questionText.setText(
                question.question == null
                        ? ""
                        : question.question
        );

        questionText.setTextSize(18);
        questionText.setTextColor(
                Color.rgb(35, 35, 35)
        );

        questionText.setLineSpacing(
                0,
                1.2f
        );

        LinearLayout.LayoutParams questionParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        questionParams.topMargin =
                dp(12);

        root.addView(
                questionText,
                questionParams
        );

        RadioGroup radioGroup = null;
        EditText shortAnswerInput = null;

        if ("choice".equals(question.type) ||
                "true_false".equals(question.type)) {

            radioGroup =
                    new RadioGroup(this);

            radioGroup.setOrientation(
                    RadioGroup.VERTICAL
            );

            String[] options =
                    question.options;

            if (options == null ||
                    options.length == 0) {

                options = new String[]{
                        "正确",
                        "错误"
                };
            }

            for (String option : options) {
                RadioButton radioButton =
                        new RadioButton(this);

                radioButton.setText(
                        option == null
                                ? ""
                                : option
                );

                radioButton.setTextSize(16);
                radioButton.setTextColor(
                        Color.rgb(60, 60, 60)
                );

                radioButton.setPadding(
                        0,
                        dp(8),
                        0,
                        dp(8)
                );

                radioGroup.addView(
                        radioButton,
                        new RadioGroup.LayoutParams(
                                -1,
                                -2
                        )
                );
            }

            LinearLayout.LayoutParams groupParams =
                    new LinearLayout.LayoutParams(
                            -1,
                            -2
                    );

            groupParams.topMargin =
                    dp(12);

            root.addView(
                    radioGroup,
                    groupParams
            );

        } else {
            shortAnswerInput =
                    new EditText(this);

            shortAnswerInput.setHint(
                    "请输入你的答案"
            );

            shortAnswerInput.setSingleLine(false);
            shortAnswerInput.setMinLines(3);
            shortAnswerInput.setGravity(
                    Gravity.TOP
            );

            shortAnswerInput.setPadding(
                    dp(12),
                    dp(12),
                    dp(12),
                    dp(12)
            );

            LinearLayout.LayoutParams answerParams =
                    new LinearLayout.LayoutParams(
                            -1,
                            -2
                    );

            answerParams.topMargin =
                    dp(16);

            root.addView(
                    shortAnswerInput,
                    answerParams
            );
        }

        final RadioGroup finalRadioGroup =
                radioGroup;

        final EditText finalShortAnswerInput =
                shortAnswerInput;

        String buttonText =
                quizIndex == quizQuestions.length - 1
                        ? "提交"
                        : "下一题";

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("AI 复习")
                        .setView(root)
                        .setNegativeButton(
                                "退出",
                                (d, which) ->
                                        confirmExitQuiz()
                        )
                        .setPositiveButton(
                                buttonText,
                                null
                        )
                        .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {

                String userAnswer = "";

                if (finalRadioGroup != null) {
                    int checkedId =
                            finalRadioGroup.getCheckedRadioButtonId();

                    if (checkedId == -1) {
                        Toast.makeText(
                                NoteDetailActivity.this,
                                "请选择一个答案",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    RadioButton selected =
                            finalRadioGroup.findViewById(
                                    checkedId
                            );

                    if (selected != null) {
                        userAnswer =
                                selected.getText().toString().trim();
                    }

                } else if (finalShortAnswerInput != null) {
                    userAnswer =
                            finalShortAnswerInput
                                    .getText()
                                    .toString()
                                    .trim();

                    if (userAnswer.isEmpty()) {
                        Toast.makeText(
                                NoteDetailActivity.this,
                                "请输入答案",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }
                }

                boolean correct =
                        checkQuizAnswer(
                                question,
                                userAnswer
                        );

                quizAnsweredCount++;

                if (correct) {
                    quizCorrectCount++;
                }

                dialog.dismiss();

                showAnswerFeedback(
                        question,
                        userAnswer,
                        correct
                );
            });
        });

        dialog.setCancelable(false);
        dialog.show();
    }

    private boolean checkQuizAnswer(
            AIClient.QuizQuestion question,
            String userAnswer
    ) {
        if (question == null ||
                question.answer == null) {
            return false;
        }

        String answer =
                normalizeAnswer(question.answer);

        String user =
                normalizeAnswer(userAnswer);

        if (user.isEmpty()) {
            return false;
        }

        if ("short_answer".equals(question.type)) {
            return checkShortAnswer(
                    user,
                    answer
            );
        }

        return user.equals(answer);
    }

    private boolean checkShortAnswer(
            String user,
            String reference
    ) {
        if (user.equals(reference)) {
            return true;
        }

        String[] tokens =
                reference.split("[\\s，。！？、；：,.!?;:]+");

        List<String> usefulTokens =
                new ArrayList<>();

        for (String token : tokens) {
            String t = token.trim();

            if (t.length() >= 2) {
                usefulTokens.add(t);
            }
        }

        if (usefulTokens.isEmpty()) {
            return false;
        }

        int matched = 0;

        for (String token : usefulTokens) {
            if (user.contains(token)) {
                matched++;
            }
        }

        int required =
                Math.max(
                        1,
                        (usefulTokens.size() + 1) / 2
                );

        return matched >= required;
    }

    private String normalizeAnswer(String text) {
        if (text == null) {
            return "";
        }

        return text
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("　", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("。", "")
                .replace(".", "")
                .replace("，", "")
                .replace(",", "");
    }

    private void showAnswerFeedback(
            AIClient.QuizQuestion question,
            String userAnswer,
            boolean correct
    ) {
        StringBuilder message =
                new StringBuilder();

        if (correct) {
            message.append("回答正确 ✓\n\n");
        } else {
            message.append("回答不正确\n\n");
        }

        message.append("你的答案：")
                .append(
                        userAnswer == null ||
                                userAnswer.isEmpty()
                                ? "未填写"
                                : userAnswer
                )
                .append("\n\n");

        message.append("参考答案：")
                .append(
                        question.answer == null
                                ? "暂无"
                                : question.answer
                );

        if (question.explanation != null &&
                !question.explanation.trim().isEmpty()) {

            message.append("\n\n解析：")
                    .append(question.explanation);
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        correct
                                ? "答对了"
                                : "看看解析"
                )
                .setMessage(
                        message.toString()
                )
                .setPositiveButton(
                        quizIndex ==
                                quizQuestions.length - 1
                                ? "查看成绩"
                                : "下一题",
                        (dialog, which) -> {
                            quizIndex++;

                            if (quizIndex >=
                                    quizQuestions.length) {
                                showQuizResult();
                            } else {
                                showQuizQuestion();
                            }
                        }
                )
                .setCancelable(false)
                .show();
    }

    private void confirmExitQuiz() {
        new AlertDialog.Builder(this)
                .setTitle("退出复习？")
                .setMessage(
                        "退出后本次复习进度不会保存。"
                )
                .setNegativeButton(
                        "继续复习",
                        null
                )
                .setPositiveButton(
                        "退出",
                        (dialog, which) -> {
                            quizQuestions = null;
                            quizIndex = 0;
                            quizCorrectCount = 0;
                            quizAnsweredCount = 0;
                        }
                )
                .show();
    }

    private void showQuizResult() {
        if (quizQuestions == null) {
            return;
        }

        int total =
                quizQuestions.length;

        int score =
                total == 0
                        ? 0
                        : Math.round(
                        quizCorrectCount
                        * 100f
                        / total
                );

        String message =
                "本次复习完成！\n\n" +
                        "答对："
                        + quizCorrectCount
                        + " / "
                        + total
                        + "\n" +
                        "得分："
                        + score
                        + " 分";

        if (score >= 90) {
            message +=
                    "\n\n很棒，掌握得不错！";
        } else if (score >= 60) {
            message +=
                    "\n\n不错，再复习一下错题会更稳。";
        } else {
            message +=
                    "\n\n建议重新看看这篇笔记，再来一次。";
        }

        new AlertDialog.Builder(this)
                .setTitle("复习结果")
                .setMessage(message)
                .setPositiveButton(
                        "完成",
                        (dialog, which) -> {
                            quizQuestions = null;
                            quizIndex = 0;
                            quizCorrectCount = 0;
                            quizAnsweredCount = 0;
                        }
                )
                .setCancelable(false)
                .show();
    }

    // ==================== 普通 AI 功能 ====================

    private void showAIResult(
            String task,
            String result
    ) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(4), dp(20), dp(4));

        TextView badge = new TextView(this);
        badge.setText("AI 生成内容");
        badge.setTextSize(12);
        badge.setTextColor(Color.rgb(107, 91, 0));
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(createIconBackground());
        root.addView(badge, new LinearLayout.LayoutParams(dp(92), dp(30)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setPadding(0, dp(10), 0, 0);

        TextView textView = new TextView(this);
        textView.setText(result);
        textView.setTextSize(16);
        textView.setTextColor(Color.rgb(45, 45, 45));
        textView.setLineSpacing(0, 1.35f);
        textView.setPadding(dp(2), dp(4), dp(2), dp(12));
        scrollView.addView(textView);

        root.addView(scrollView, new LinearLayout.LayoutParams(-1, dp(360), 1));

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(task)
                .setView(root);

        if ("润色这篇笔记".equals(task)) {
            builder.setNegativeButton(
                    "替换原笔记",
                    (dialog, which) -> replaceNoteContent(result)
            );
        }

        builder.setPositiveButton("关闭", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(createDialogBackground());
            dialog.getWindow().setDimAmount(0.28f);
        });
        dialog.show();
    }

    private void replaceNoteContent(
            String newContent
    ) {
        if (newContent == null ||
                newContent.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "AI 返回的内容为空，无法替换",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (currentNote == null) {
            Toast.makeText(
                    this,
                    "当前笔记不存在",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("替换原笔记？")
                .setMessage(
                        "替换后将使用 AI 润色后的内容覆盖当前笔记内容。"
                )
                .setNegativeButton(
                        "取消",
                        null
                )
                .setPositiveButton(
                        "替换",
                        (dialog, which) -> {

                            currentNote.setContent(
                                    newContent
                            );

                            new Thread(() -> {

                                noteDao.update(
                                        currentNote
                                );

                                runOnUiThread(() -> {

                                    contentEditText.setText(
                                            newContent
                                    );

                                    Toast.makeText(
                                            this,
                                            "笔记已替换",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                });

                            }).start();
                        }
                )
                .show();
    }

    private void showAIError(
            String error
    ) {
        new AlertDialog.Builder(this)
                .setTitle(
                        "AI 请求失败"
                )
                .setMessage(
                        error
                )
                .setPositiveButton(
                        "确定",
                        null
                )
                .show();
    }

    // ==================== 文件夹 ====================

    private void scheduleAutoSave() {
        if (currentNote == null) {
            return;
        }

        if (saveRunnable != null) {
            handler.removeCallbacks(saveRunnable);
        }

        saveRunnable =
                this::saveNoteImmediately;

        handler.postDelayed(
                saveRunnable,
                500
        );
    }

    private void saveNoteImmediately() {
        if (currentNote == null) {
            return;
        }

        String title =
                titleEditText.getText().toString();

        String content =
                contentEditText.getText().toString();

        currentNote.setTitle(title);
        currentNote.setContent(content);

        new Thread(
                () -> noteDao.update(currentNote)
        ).start();
    }

    private void showFolderDialog() {
        if (currentNote == null) {
            Toast.makeText(
                    this,
                    "笔记还没有加载完成",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        new Thread(() -> {
            try {
                List<Folder> folders = folderDao.getAllFolders();
                if (folders == null) {
                    folders = new ArrayList<>();
                }

                final List<Folder> finalFolders = folders;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    LinearLayout root = new LinearLayout(this);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setPadding(dp(20), dp(4), dp(20), dp(4));

                    TextView subtitle = new TextView(this);
                    subtitle.setText("选择这篇笔记要放入的文件夹");
                    subtitle.setTextSize(13);
                    subtitle.setTextColor(Color.rgb(120, 120, 120));
                    root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(34)));

                    addFolderOption(root, "未分类", currentNote.getFolderId() == null,
                            () -> moveToFolder(null, "未分类"));

                    for (Folder folder : finalFolders) {
                        if (folder == null || folder.getName() == null) {
                            continue;
                        }
                        Integer folderId = folder.getId();
                        boolean selected = currentNote.getFolderId() != null
                                && currentNote.getFolderId().equals(folderId);
                        addFolderOption(root, folder.getName(), selected,
                                () -> moveToFolder(folderId, folder.getName()));
                    }

                    ScrollView scrollView = new ScrollView(this);
                    scrollView.addView(root);

                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setTitle("移动")
                            .setView(scrollView)
                            .setNegativeButton("取消", null)
                            .create();

                    dialog.setOnShowListener(d -> {
                        dialog.getWindow().setBackgroundDrawable(createDialogBackground());
                        dialog.getWindow().setDimAmount(0.28f);
                    });

                    dialog.show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(
                        NoteDetailActivity.this,
                        "读取文件夹失败：" + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
            }
        }).start();
    }

    private void addFolderOption(
            LinearLayout parent,
            String name,
            boolean selected,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setBackground(selected ? createSelectedFolderBackground() : createOptionBackground());
        row.setClickable(true);

        TextView icon = new TextView(this);
        icon.setText("▣");
        icon.setTextSize(16);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(selected ? Color.rgb(107, 91, 0) : Color.rgb(100, 100, 100));
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(42)));

        TextView text = new TextView(this);
        text.setText(name);
        text.setTextSize(15);
        text.setTextColor(Color.rgb(35, 35, 35));
        text.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(0, dp(54), 1));

        TextView check = new TextView(this);
        check.setText(selected ? "✓" : "");
        check.setTextSize(18);
        check.setTextColor(Color.rgb(107, 91, 0));
        check.setGravity(Gravity.CENTER);
        row.addView(check, new LinearLayout.LayoutParams(dp(30), dp(54)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.bottomMargin = dp(6);
        parent.addView(row, params);

        row.setOnClickListener(v -> action.run());
    }

    private GradientDrawable createSelectedFolderBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(255, 248, 225));
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private void moveToFolder(
            Integer folderId,
            String folderName
    ) {
        if (currentNote == null) {
            return;
        }

        new Thread(() -> {
            try {
                noteDao.moveNoteToFolder(
                        currentNote.getId(),
                        folderId
                );

                currentNote.setFolderId(folderId);

                runOnUiThread(() ->
                        Toast.makeText(
                                NoteDetailActivity.this,
                                "已移动到：" + folderName,
                                Toast.LENGTH_SHORT
                        ).show()
                );

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(
                                NoteDetailActivity.this,
                                "移动失败："
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }).start();
    }

    private void confirmDelete() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(2), dp(20), dp(4));

        TextView icon = new TextView(this);
        icon.setText("×");
        icon.setTextSize(28);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.rgb(185, 70, 60));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.rgb(255, 240, 238));
        iconBg.setCornerRadius(dp(18));
        icon.setBackground(iconBg);
        root.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView message = new TextView(this);
        message.setText("确定要删除这篇笔记吗？\n删除后将无法在 MINENOTE 中恢复。" );
        message.setTextSize(15);
        message.setTextColor(Color.rgb(70, 70, 70));
        message.setLineSpacing(0, 1.25f);
        message.setPadding(0, dp(14), 0, dp(6));
        root.addView(message, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除笔记")
                .setView(root)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, which) -> deleteNote())
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(createDialogBackground());
            dialog.getWindow().setDimAmount(0.32f);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(Color.rgb(190, 65, 55));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(Color.rgb(90, 90, 90));
        });

        dialog.show();
    }

    private void deleteNote() {
        if (currentNote == null) {
            return;
        }

        Note noteToDelete =
                currentNote;

        new Thread(() -> {
            noteDao.delete(noteToDelete);

            runOnUiThread(() -> {
                Toast.makeText(
                        NoteDetailActivity.this,
                        "笔记已删除",
                        Toast.LENGTH_SHORT
                ).show();

                finish();
            });
        }).start();
    }

    private int dp(int value) {
        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + 0.5f
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNoteImmediately();
    }

    @Override
    protected void onDestroy() {
        if (saveRunnable != null) {
            handler.removeCallbacks(saveRunnable);
        }

        if (database != null &&
                database.isOpen()) {
            database.close();
        }

        super.onDestroy();
    }
}
