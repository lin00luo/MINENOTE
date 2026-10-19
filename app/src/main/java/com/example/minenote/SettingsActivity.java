package com.example.minenote;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private Spinner spinnerProvider;
    private EditText etApiKey;
    private EditText etBaseUrl;
    private EditText etModel;
    private TextView btnSave;

    private SharedPreferences preferences;

    private final String[] providers = {
            "OpenAI",
            "DeepSeek",
            "自定义"
    };

    private boolean isInitializing = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        // 获取控件
        spinnerProvider = findViewById(R.id.spinnerProvider);
        etApiKey = findViewById(R.id.etApiKey);
        etBaseUrl = findViewById(R.id.etBaseUrl);
        etModel = findViewById(R.id.etModel);
        btnSave = findViewById(R.id.btnSave);

        // SharedPreferences
        preferences = getSharedPreferences(
                "ai_settings",
                MODE_PRIVATE
        );

        // =========================
        // 设置 AI 服务商下拉框
        // =========================

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        providers
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerProvider.setAdapter(adapter);

        // =========================
        // 读取已经保存的设置
        // =========================

        String savedProvider =
                preferences.getString(
                        "provider",
                        "OpenAI"
                );

        String savedApiKey =
                preferences.getString(
                        "api_key",
                        ""
                );

        String savedBaseUrl =
                preferences.getString(
                        "base_url",
                        ""
                );

        String savedModel =
                preferences.getString(
                        "model",
                        ""
                );

        // 设置服务商
        for (int i = 0; i < providers.length; i++) {

            if (providers[i].equals(savedProvider)) {

                spinnerProvider.setSelection(i);

                break;
            }
        }

        // 设置 API Key
        etApiKey.setText(savedApiKey);

        // 设置 Base URL
        if (!savedBaseUrl.isEmpty()) {

            etBaseUrl.setText(savedBaseUrl);

        } else {

            setDefaultValues(
                    spinnerProvider.getSelectedItemPosition()
            );
        }

        // 设置模型
        if (!savedModel.isEmpty()) {

            etModel.setText(savedModel);
        }

        isInitializing = false;

        // =========================
        // 切换 AI 服务商
        // =========================

        spinnerProvider.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            android.view.View view,
                            int position,
                            long id
                    ) {

                        if (isInitializing) {
                            return;
                        }

                        setDefaultValues(position);
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                    }
                }
        );

        // =========================
        // 保存按钮
        // =========================

        btnSave.setOnClickListener(v -> saveSettings());
    }

    // =========================================================
    // 设置默认地址和模型
    // =========================================================

    private void setDefaultValues(int position) {

        if (position == 0) {

            // OpenAI

            etBaseUrl.setText(
                    "https://api.openai.com/v1"
            );

            etModel.setText(
                    "gpt-4o-mini"
            );

        } else if (position == 1) {

            // DeepSeek

            etBaseUrl.setText(
                    "https://api.deepseek.com/v1"
            );

            etModel.setText(
                    "deepseek-chat"
            );

        } else {

            // 自定义

            etBaseUrl.setText("");

            etModel.setText("");
        }
    }

    // =========================================================
    // 保存设置
    // =========================================================

    private void saveSettings() {

        String provider =
                spinnerProvider
                        .getSelectedItem()
                        .toString();

        String apiKey =
                etApiKey
                        .getText()
                        .toString()
                        .trim();

        String baseUrl =
                etBaseUrl
                        .getText()
                        .toString()
                        .trim();

        String model =
                etModel
                        .getText()
                        .toString()
                        .trim();

        // 基本检查
        if (apiKey.isEmpty()) {

            Toast.makeText(
                    this,
                    "请输入 API Key",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (baseUrl.isEmpty()) {

            Toast.makeText(
                    this,
                    "请输入 Base URL",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (model.isEmpty()) {

            Toast.makeText(
                    this,
                    "请输入模型名称",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // 保存
        preferences.edit()
                .putString(
                        "provider",
                        provider
                )
                .putString(
                        "api_key",
                        apiKey
                )
                .putString(
                        "base_url",
                        baseUrl
                )
                .putString(
                        "model",
                        model
                )
                .apply();

        Toast.makeText(
                this,
                "设置已保存",
                Toast.LENGTH_SHORT
        ).show();

        finish();
    }
}
