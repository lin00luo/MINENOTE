package com.example.minenote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/** Shared editor actions. Call constructor during Activity.onCreate, before STARTED. */
public final class EditorFeatures {
    private final AppCompatActivity activity;
    private final EditText title;
    private final EditText body;
    private final ActivityResultLauncher<Intent> ocr;
    private final ActivityResultLauncher<String> image;
    private final ActivityResultLauncher<String> saveDocument;
    private String exportFormat="pdf";
    public EditorFeatures(AppCompatActivity activity, EditText title, EditText body) {
        this.activity=activity;this.title=title;this.body=body;
        ocr=activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r->{
            if(r.getResultCode()==Activity.RESULT_OK && r.getData()!=null){String value=r.getData().getStringExtra("recognized_text");if(value!=null)insert("\n"+value+"\n");}
        });
        image=activity.registerForActivityResult(new ActivityResultContracts.GetContent(), uri->{if(uri!=null)InlineImages.insert(activity,body,uri);});
        saveDocument=activity.registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"), uri->{if(uri!=null)export(uri);});
    }
    public void openOcr(){ocr.launch(new Intent(activity,OcrActivity.class));}
    public void pickImage(){image.launch("image/*");}
    public void showExport(){new AlertDialog.Builder(activity).setTitle("导出笔记").setItems(new String[]{"PDF · 图文排版","TXT · 纯文本","Markdown · 含内嵌图片"},(dialog,which)->{
        exportFormat=new String[]{"pdf","txt","md"}[which];
        String name=title.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]","_");
        if(name.isEmpty())name="MINENOTE笔记";
        saveDocument.launch(name+"."+exportFormat);
    }).show();}
    private void export(Uri uri){
        String t=title.getText().toString(),content=body.getText().toString(),format=exportFormat;
        new Thread(()->{try{NoteExporter.export(activity,uri,format,t,content);activity.runOnUiThread(()->Toast.makeText(activity,"导出成功",Toast.LENGTH_SHORT).show());}catch(Exception e){activity.runOnUiThread(()->Toast.makeText(activity,"导出失败："+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();
    }
    private void insert(String value){int pos=Math.max(0,body.getSelectionStart());body.getText().insert(Math.min(pos,body.length()),value);}
}
