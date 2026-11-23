package com.example.minenote;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.io.File;

public class OcrActivity extends AppCompatActivity {
    private ImageView preview;
    private EditText result;
    private ProgressBar progress;
    private Uri photoUri;
    private String originalText="";
    private String optimizedText="";
    private boolean recognizing=false, optimizing=false;
    private Button optimizeButton, originalButton, optimizedButton, insertButton;
    private TextView status;
    private CheckBox autoOptimize;
    private boolean showingOptimized=false;
    private long requestVersion=0;
    private static final String PREFS="ocr_settings";
    private static final String AUTO="auto_optimize";
    private ActivityResultLauncher<Uri> camera;
    private ActivityResultLauncher<String> gallery;
    private int dp(int n) {return (int)(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        camera=registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {if(ok && photoUri!=null) recognize(photoUri);});
        gallery=registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {if(uri!=null) recognize(uri);});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(42),dp(18),dp(12));root.setBackgroundColor(Color.rgb(250,250,250));
        TextView heading=new TextView(this);heading.setText("‹    图片文字识别");heading.setTextColor(Color.rgb(35,35,35));heading.setTextSize(22);heading.setPadding(0,0,0,dp(16));heading.setOnClickListener(v->finish());root.addView(heading);
        TextView hint=new TextView(this);hint.setText("选择照片，识别后可检查并修改文字，再插入笔记。");hint.setTextSize(14);hint.setTextColor(Color.GRAY);root.addView(hint);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(12),0,dp(12));
        Button shoot=new Button(this);shoot.setText("拍照");actions.addView(shoot,new LinearLayout.LayoutParams(0,dp(52),1));
        Button pick=new Button(this);pick.setText("相册");actions.addView(pick,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(actions);
        shoot.setOnClickListener(v->openCamera());pick.setOnClickListener(v->gallery.launch("image/*"));
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setMaxHeight(dp(280));content.addView(preview,new LinearLayout.LayoutParams(-1,dp(220)));
        progress=new ProgressBar(this);progress.setVisibility(View.GONE);content.addView(progress,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView label=new TextView(this);label.setText("识别结果（可编辑）");label.setTextSize(16);label.setPadding(0,dp(12),0,dp(8));content.addView(label);
        result=new EditText(this);result.setGravity(Gravity.TOP);result.setMinLines(8);result.setTextSize(16);result.setHint("识别结果会显示在这里");content.addView(result,new LinearLayout.LayoutParams(-1,dp(250)));
        TextView privacy=new TextView(this);privacy.setText("AI 优化会将识别出的文字发送到当前配置的 AI 服务商，可能产生 Token 费用；图片本身不会上传。");privacy.setTextSize(12);privacy.setTextColor(Color.GRAY);content.addView(privacy);
        autoOptimize=new CheckBox(this);autoOptimize.setText("自动 AI 优化（可随时关闭）");
        SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        autoOptimize.setChecked(prefs.getBoolean(AUTO,false));
        autoOptimize.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean(AUTO,checked).apply());
        content.addView(autoOptimize);
        LinearLayout modes=new LinearLayout(this);
        originalButton=new Button(this);originalButton.setText("OCR 原文");modes.addView(originalButton,new LinearLayout.LayoutParams(0,dp(48),1));
        optimizedButton=new Button(this);optimizedButton.setText("AI 优化结果");modes.addView(optimizedButton,new LinearLayout.LayoutParams(0,dp(48),1));content.addView(modes);
        originalButton.setOnClickListener(v->switchView(false));
        optimizedButton.setOnClickListener(v->switchView(true));
        optimizeButton=new Button(this);optimizeButton.setText("✨ AI 智能纠错与排版");content.addView(optimizeButton,new LinearLayout.LayoutParams(-1,dp(54)));
        optimizeButton.setOnClickListener(v->optimize());
        status=new TextView(this);status.setText("默认手动优化 · OCR 结果可直接编辑");status.setTextSize(13);status.setTextColor(Color.DKGRAY);status.setPadding(0,dp(8),0,dp(10));content.addView(status);
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        insertButton=new Button(this);insertButton.setText("确认并插入当前笔记");root.addView(insertButton,new LinearLayout.LayoutParams(-1,dp(56)));
        insertButton.setOnClickListener(v->{String value=result.getText().toString();if(value.trim().isEmpty()){Toast.makeText(this,"没有可插入的文字",Toast.LENGTH_SHORT).show();return;}Intent data=new Intent();data.putExtra("recognized_text",value);setResult(Activity.RESULT_OK,data);finish();});
        setContentView(root);
    }
    private void openCamera(){try{File dir=new File(getCacheDir(),"ocr_photos");if(!dir.exists()&&!dir.mkdirs())throw new Exception("无法创建缓存");File file=File.createTempFile("ocr_",".jpg",dir);photoUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);camera.launch(photoUri);}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void recognize(Uri uri){
        requestVersion++;recognizing=true;optimizing=false;result.setEnabled(true);
        originalText="";optimizedText="";showingOptimized=false;result.setText("");
        preview.setImageURI(uri);progress.setVisibility(View.VISIBLE);status.setText("正在识别图片……");updateButtons();
        try {
            InputImage image=InputImage.fromFilePath(this,uri);
            com.google.mlkit.vision.text.TextRecognizer recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            final long version=requestVersion;
            recognizer.process(image).addOnSuccessListener(text->{
                if(version!=requestVersion)return;
                originalText=text.getText();result.setText(originalText);
                if(originalText.trim().isEmpty()){status.setText("没有识别到文字，请更换图片");return;}
                status.setText("OCR 识别完成。可手动修改，或使用 AI 优化。");
            }).addOnFailureListener(e->{if(version==requestVersion)status.setText("识别失败："+e.getMessage());}).addOnCompleteListener(task->{recognizer.close();if(version==requestVersion){recognizing=false;progress.setVisibility(View.GONE);updateButtons();if(task.isSuccessful() && autoOptimize.isChecked() && !originalText.trim().isEmpty())optimize();}});
        } catch(Exception e){progress.setVisibility(View.GONE);Toast.makeText(this,"无法读取图片",Toast.LENGTH_LONG).show();}
    }
    private void saveCurrentEdits(){
        if(showingOptimized)optimizedText=result.getText().toString();
        else originalText=result.getText().toString();
    }
    private void switchView(boolean optimized){
        if(recognizing||optimizing)return;
        if(optimized && optimizedText.isEmpty()){Toast.makeText(this,"请先执行 AI 优化",Toast.LENGTH_SHORT).show();return;}
        saveCurrentEdits();showingOptimized=optimized;
        result.setText(optimized?optimizedText:originalText);
        status.setText(optimized?"正在查看 AI 优化结果（可编辑）":"正在查看 OCR 原文（可编辑）");
        updateButtons();
    }
    private void updateButtons(){
        if(optimizeButton==null)return;
        optimizeButton.setEnabled(!recognizing&&!optimizing);
        originalButton.setEnabled(!recognizing&&!optimizing);
        optimizedButton.setEnabled(!recognizing&&!optimizing&&!optimizedText.isEmpty());
        insertButton.setEnabled(!recognizing&&!optimizing);
        optimizeButton.setText(optimizing?"AI 优化中，请稍候……":"✨ AI 智能纠错与排版");
    }
    private void optimize(){
        if(recognizing||optimizing)return;
        saveCurrentEdits();
        String input=originalText.trim();
        if(input.isEmpty()){Toast.makeText(this,"请先识别图片",Toast.LENGTH_SHORT).show();return;}
        SharedPreferences settings=getSharedPreferences("ai_settings",MODE_PRIVATE);
        if(settings.getString("api_key","").trim().isEmpty()||settings.getString("model","").trim().isEmpty()){
            new AlertDialog.Builder(this).setTitle("尚未配置 AI")
                .setMessage("请先在 Minenote 的 AI 设置中填写 API Key 和模型。原始 OCR 结果仍可正常使用。")
                .setPositiveButton("知道了",null).show();return;
        }
        optimizing=true;result.setEnabled(false);status.setText("正在使用 AI 纠错和整理排版，可能产生 Token 费用……");updateButtons();
        final long version=requestVersion;
        String task="对以下 OCR 识别文本执行【保守纠错 + 排版】，这是文本修复任务，不是创作任务。严格要求："+
            "1. 保持原有信息、句序、数字、单位、公式、代码和专有名词；"+
            "2. 仅修复上下文能够确定的错别字、标点、断行、空格；"+
            "3. 恢复明显的标题、段落和编号结构；"+
            "4. 对不确定内容原样保留，绝不猜测补全、删节、总结或扩写；"+
            "5. 不要输出解释、引言、Markdown 代码围栏；只返回修复后的完整文字。";
        new AIClient(this).analyzeNote(input,task,new AIClient.Callback(){
            @Override public void onSuccess(String text){runOnUiThread(()->{
                if(isFinishing()||version!=requestVersion)return;
                optimizing=false;result.setEnabled(true);
                if(text==null||text.trim().isEmpty()){status.setText("AI 返回空结果，已保留 OCR 原文");updateButtons();return;}
                saveCurrentEdits();optimizedText=text.trim();showingOptimized=true;result.setText(optimizedText);
                status.setText("AI 优化完成。请对照图片和 OCR 原文检查，确认后插入笔记。");updateButtons();
            });}
            @Override public void onError(String error){runOnUiThread(()->{
                if(isFinishing()||version!=requestVersion)return;
                optimizing=false;result.setEnabled(true);status.setText("AI 优化失败："+error+"。OCR 原文已保留。");updateButtons();
            });}
        });
    }
}
