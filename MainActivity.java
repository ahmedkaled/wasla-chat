package com.wasla.chat;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends Activity {
    private final int teal = Color.rgb(7, 94, 102);
    private LinearLayout messages;
    private TextView status;
    private EditText nameInput, ipInput, messageInput;
    private ServerSocket serverSocket;
    private volatile boolean hosting = false;
    private final CopyOnWriteArrayList<PrintWriter> clients = new CopyOnWriteArrayList<>();
    private PrintWriter serverWriter;
    private Socket clientSocket;
    private BufferedReader clientReader;
    private Thread clientThread;
    private String userName = "مستخدم";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(teal);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(245,248,249));
        TextView title = new TextView(this); title.setText("وصلة  |  WASLA"); title.setTextSize(24); title.setTextColor(Color.WHITE); title.setGravity(Gravity.CENTER_VERTICAL); title.setPadding(20,16,20,16); title.setBackgroundColor(teal); root.addView(title, new LinearLayout.LayoutParams(-1, dp(62)));
        TextView note = new TextView(this); note.setText("شات محلي بدون إنترنت • كل الأجهزة على نفس شبكة الواي فاي"); note.setTextColor(Color.DKGRAY); note.setPadding(12,10,12,10); root.addView(note);
        LinearLayout setup = new LinearLayout(this); setup.setPadding(10,4,10,4); setup.setOrientation(LinearLayout.VERTICAL);
        nameInput = new EditText(this); nameInput.setSingleLine(true); nameInput.setHint("اسمك"); nameInput.setText("أحمد"); setup.addView(nameInput, new LinearLayout.LayoutParams(-1, dp(48)));
        ipInput = new EditText(this); ipInput.setSingleLine(true); ipInput.setHint("IP الجهاز المستضيف (مثال 192.168.43.1)"); ipInput.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); setup.addView(ipInput, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout buttons = new LinearLayout(this); Button host = new Button(this); host.setText("استضافة شات"); Button join = new Button(this); join.setText("اتصال بالمضيف"); buttons.addView(host,new LinearLayout.LayoutParams(0,dp(48),1)); buttons.addView(join,new LinearLayout.LayoutParams(0,dp(48),1)); setup.addView(buttons); root.addView(setup);
        status = new TextView(this); status.setText("غير متصل"); status.setTextColor(teal); status.setPadding(12,6,12,6); root.addView(status);
        ScrollView scroll = new ScrollView(this); messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(12,8,12,8); scroll.addView(messages); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composer = new LinearLayout(this); composer.setPadding(8,4,8,8); messageInput = new EditText(this); messageInput.setHint("اكتب رسالة..."); composer.addView(messageInput,new LinearLayout.LayoutParams(0,dp(54),1)); Button send = new Button(this); send.setText("إرسال"); composer.addView(send,new LinearLayout.LayoutParams(dp(88),dp(54))); root.addView(composer);
        setContentView(root);
        host.setOnClickListener(v -> startHosting()); join.setOnClickListener(v -> joinHost()); send.setOnClickListener(v -> sendMessage());
        addMessage("النظام", "للتجربة: وصّل الموبايلات بنفس الواي فاي أو فعّل Hotspot. اضغط استضافة على جهاز، ثم أدخل عنوان IP بتاعه في الأجهزة الأخرى.");
    }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private void ui(Runnable r){ runOnUiThread(r); }
    private void startHosting(){
        userName = safeName();
        if(hosting){ toast("الاستضافة شغالة بالفعل"); return; }
        new Thread(() -> { try {
            serverSocket = new ServerSocket(8988); hosting = true;
            ui(() -> status.setText("أنت المضيف • المنفذ 8988 • اعرف IP جهازك من إعدادات الواي فاي"));
            addMessage("النظام", "بدأت الاستضافة. لازم الأجهزة الأخرى تكون على نفس الشبكة وتدخل عنوان IP لهذا الموبايل.");
            while(hosting){ Socket s = serverSocket.accept(); new Thread(() -> handleClient(s)).start(); }
        } catch(Exception e){ ui(() -> status.setText("تعذر بدء الاستضافة: "+e.getMessage())); } }).start();
    }
    private void handleClient(Socket socket){
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true); clients.add(out);
            String line; while((line=in.readLine())!=null){ broadcast(line); }
        } catch(Exception ignored){} finally { try{socket.close();}catch(Exception ignored){} }
    }
    private void joinHost(){
        userName = safeName(); String ip = ipInput.getText().toString().trim();
        if(ip.isEmpty()){ toast("اكتب IP جهاز الاستضافة الأول"); return; }
        new Thread(() -> { try {
            clientSocket = new Socket(); clientSocket.connect(new InetSocketAddress(ip,8988),5000);
            serverWriter = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"),true);
            clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(),"UTF-8"));
            ui(() -> status.setText("متصل بالمضيف: "+ip));
            clientThread = new Thread(() -> { try { String line; while((line=clientReader.readLine())!=null) { String msg=line; ui(() -> displayWireMessage(msg)); } } catch(Exception e){ ui(() -> status.setText("انقطع الاتصال")); } }); clientThread.start();
        } catch(Exception e){ ui(() -> status.setText("فشل الاتصال. راجع IP والشبكة")); } }).start();
    }
    private String safeName(){ String n=nameInput.getText().toString().trim(); return n.isEmpty()?"مستخدم":n.replace("|"," ").replace("\n"," "); }
    private void sendMessage(){ String text=messageInput.getText().toString().trim(); if(text.isEmpty())return; userName=safeName(); String wire=userName+"|"+text.replace("\n"," ");
        if(hosting){ broadcast(wire); } else if(serverWriter!=null){ serverWriter.println(wire); } else { addMessage("النظام","ابدأ الاستضافة أو اتصل بمضيف قبل الإرسال."); return; }
        messageInput.setText(""); ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(messageInput.getWindowToken(),0);
    }
    private void broadcast(String line){ for(PrintWriter p:clients){ p.println(line); } ui(() -> displayWireMessage(line)); }
    private void displayWireMessage(String line){ int i=line.indexOf('|'); if(i<0){addMessage("رسالة",line);return;} addMessage(line.substring(0,i),line.substring(i+1)); }
    private void addMessage(String who,String text){ ui(() -> { TextView bubble=new TextView(this); bubble.setText(who+"\n"+text); bubble.setTextSize(16); bubble.setTextColor(Color.rgb(35,45,48)); bubble.setBackgroundColor(Color.WHITE); bubble.setPadding(14,10,14,10); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(8)); messages.addView(bubble,lp); messages.post(() -> ((ScrollView)messages.getParent()).fullScroll(View.FOCUS_DOWN)); }); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    @Override protected void onDestroy(){ super.onDestroy(); hosting=false; try{if(serverSocket!=null)serverSocket.close();}catch(Exception ignored){} try{if(clientSocket!=null)clientSocket.close();}catch(Exception ignored){} }
}
