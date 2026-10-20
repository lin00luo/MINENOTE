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
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button insert=new Button(this);insert.setText("插入当前笔记");root.addView(insert,new LinearLayout.LayoutParams(-1,dp(56)));
        insert.setOnClickListener(v->{String value=result.getText().toString();if(value.trim().isEmpty()){Toast.makeText(this,"没有可插入的文字",Toast.LENGTH_SHORT).show();return;}Intent data=new Intent();data.putExtra("recognized_text",value);setResult(Activity.RESULT_OK,data);finish();});
        setContentView(root);
    }
    private void openCamera(){try{File dir=new File(getCacheDir(),"ocr_photos");if(!dir.exists()&&!dir.mkdirs())throw new Exception("无法创建缓存");File file=File.createTempFile("ocr_",".jpg",dir);photoUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);camera.launch(photoUri);}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void recognize(Uri uri){
        preview.setImageURI(uri);progress.setVisibility(View.VISIBLE);
        try {
            InputImage image=InputImage.fromFilePath(this,uri);
            com.google.mlkit.vision.text.TextRecognizer recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            recognizer.process(image).addOnSuccessListener(text->{result.setText(text.getText());if(text.getText().trim().isEmpty())Toast.makeText(this,"没有识别到文字",Toast.LENGTH_SHORT).show();}).addOnFailureListener(e->Toast.makeText(this,"识别失败："+e.getMessage(),Toast.LENGTH_LONG).show()).addOnCompleteListener(task->{progress.setVisibility(View.GONE);recognizer.close();});
        } catch(Exception e){progress.setVisibility(View.GONE);Toast.makeText(this,"无法读取图片",Toast.LENGTH_LONG).show();}
    }
}
