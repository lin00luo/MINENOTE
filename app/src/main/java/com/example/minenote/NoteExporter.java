package com.example.minenote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

public final class NoteExporter {
    private NoteExporter() {}
    public static void export(Context context, Uri destination, String format, String title, String body) throws Exception {
        try (OutputStream out = context.getContentResolver().openOutputStream(destination)) {
            if (out == null) throw new Exception("无法打开导出文件");
            if ("pdf".equals(format)) {
                pdf(context, out, title, body);
            } else {
                String text = title + "\n\n" + ("md".equals(format) ? markdown(context, body) : plain(body));
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    private static String plain(String body) {
        return InlineImages.MARKER.matcher(body).replaceAll("[图片]");
    }
    private static String markdown(Context context, String body) throws Exception {
        Matcher m = InlineImages.MARKER.matcher(body);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            File file = InlineImages.file(context, m.group(1));
            String replacement = "![图片缺失]()";
            if (file.isFile()) {
                byte[] bytes = new byte[(int)file.length()];
                try (FileInputStream in = new FileInputStream(file)) {
                    int n = 0, r;
                    while (n < bytes.length && (r = in.read(bytes, n, bytes.length - n)) > 0) n += r;
                    if (n == bytes.length) replacement = "![图片](data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP) + ")";
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
    private static void pdf(Context context, OutputStream out, String title, String body) throws Exception {
        PdfDocument document = new PdfDocument();
        final int pageW=595, pageH=842, margin=42, usable=pageW-2*margin;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK); paint.setTextSize(14);
        PdfDocument.Page page = null;
        Canvas canvas = null;
        int y=margin, pageNumber=0;
        try {
            String full = title + "\n\n" + body;
            Matcher matcher = InlineImages.MARKER.matcher(full);
            int position=0;
            while (position < full.length()) {
                matcher.region(position, full.length());
                boolean found=matcher.find();
                int end=found ? matcher.start() : full.length();
                String section=full.substring(position,end);
                for (String paragraph:section.split("\n",-1)) {
                    if (paragraph.isEmpty()) {
                        if (page==null || y+22>pageH-margin) { if(page!=null)document.finishPage(page); page=document.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,++pageNumber).create());canvas=page.getCanvas();y=margin; }
                        y+=19; continue;
                    }
                    StringBuilder line=new StringBuilder();
                    for(int offset=0;offset<paragraph.length();) {
                        int cp=paragraph.codePointAt(offset); String character=new String(Character.toChars(cp));
                        if(line.length()>0 && paint.measureText(line.toString()+character)>usable) {
                            if(page==null || y+24>pageH-margin){if(page!=null)document.finishPage(page);page=document.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,++pageNumber).create());canvas=page.getCanvas();y=margin;}
                            canvas.drawText(line.toString(),margin,y+15,paint);y+=23;line.setLength(0);
                        }
                        line.append(character);offset+=Character.charCount(cp);
                    }
                    if(page==null || y+24>pageH-margin){if(page!=null)document.finishPage(page);page=document.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,++pageNumber).create());canvas=page.getCanvas();y=margin;}
                    canvas.drawText(line.toString(),margin,y+15,paint);y+=23;
                }
                if(!found)break;
                File f=InlineImages.file(context,matcher.group(1));
                Bitmap bitmap=BitmapFactory.decodeFile(f.getAbsolutePath());
                if(bitmap!=null) {
                    float scale=Math.min((float)usable/bitmap.getWidth(),(float)(pageH-2*margin)/bitmap.getHeight());
                    int width=Math.max(1,(int)(bitmap.getWidth()*scale)),height=Math.max(1,(int)(bitmap.getHeight()*scale));
                    if(page==null || y+height>pageH-margin){if(page!=null)document.finishPage(page);page=document.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,++pageNumber).create());canvas=page.getCanvas();y=margin;}
                    canvas.drawBitmap(bitmap,null,new android.graphics.Rect(margin,y,margin+width,y+height),null);y+=height+12;bitmap.recycle();
                }
                position=matcher.end();
            }
            if(page==null) {page=document.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,1).create());}
            document.finishPage(page);
            document.writeTo(out);
        } finally {document.close();}
    }
}
