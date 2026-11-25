package com.autio.notes;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.*;
import java.io.File;

public class DeliveryActivity extends Activity {
    private TextView destination,status;
    private Button retry;
    private int d(int n) { return Ui.dp(this,n); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=Ui.column(this); root.setPadding(d(24),d(12),d(24),d(24));
        Button back=Ui.button(this,"Settings","back",Ui.MINT,Ui.BG,0); root.addView(back,new LinearLayout.LayoutParams(-2,d(52))); back.setOnClickListener(v->finish()); Ui.gap(root,24);
        TextView title=Ui.text(this,"Note delivery",32,Ui.TEXT); Ui.medium(title); root.addView(title); Ui.gap(root,12);
        root.addView(Ui.text(this,"This folder is your library. After a verified delivery, Autio releases its private staging copy. Syncthing manages transfer; deleting a delivered note here removes it from the library.",16,Ui.MUTED)); Ui.gap(root,24);
        destination=Ui.text(this,NoteDelivery.folderLabel(this),18,Ui.TEXT); root.addView(destination); Ui.gap(root,16);
        Button choose=Ui.button(this,"Choose sync folder",null,Ui.BG,Ui.MINT,0); root.addView(choose,new LinearLayout.LayoutParams(-1,d(60)));
        choose.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            String previous=NoteDelivery.folder(this);
            Uri initial=previous==null?DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:syncthing/Recordings"):Uri.parse(previous);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,initial); startActivityForResult(intent,1);
        });
        Ui.gap(root,24);
        Switch wake=new Switch(this); wake.setText("Start Syncthing after saving"); wake.setTextColor(Ui.TEXT); wake.setTextSize(17); wake.setChecked(NoteDelivery.prefs(this).getBoolean("wake",true)); wake.setPadding(0,d(8),0,d(8)); root.addView(wake);
        wake.setOnCheckedChangeListener((button,value)->NoteDelivery.prefs(this).edit().putBoolean("wake",value).apply());
        TextView hint=Ui.text(this,"Requires Service Control by Broadcast in Syncthing-Fork. Start runs it even outside its usual run conditions; Autio does not stop it afterward.",13,Ui.MUTED); hint.setLineSpacing(d(4),1); root.addView(hint); Ui.gap(root,24);
        retry=Ui.button(this,"Retry pending delivery",null,Ui.TEXT,Ui.SURFACE,Ui.LINE); root.addView(retry,new LinearLayout.LayoutParams(-1,d(60))); retry.setOnClickListener(v->copyNotes()); Ui.gap(root,16);
        status=Ui.text(this,"Autio verifies the folder copy and retries pending deliveries. Syncthing handles transfer. "+DeliveryJobs.status,14,Ui.MUTED); status.setLineSpacing(d(4),1); root.addView(status);
        ScrollView scroll=new ScrollView(this); scroll.addView(root); Ui.install(this,scroll);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=1||result!=RESULT_OK||data==null||data.getData()==null) return;
        Uri tree=data.getData();
        try {
            int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if(flags!=(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) throw new java.io.IOException("Folder needs read and write access");
            getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            NoteDelivery.prefs(this).edit().putString("folder",tree.toString()).apply(); destination.setText(NoteDelivery.folderLabel(this));
            status.setText("Folder selected. Pending notes will be delivered automatically."); DeliveryJobs.kick(this);
        } catch(Exception e) { status.setText("Could not keep folder access: "+e.getMessage()); }
    }
    private void copyNotes() {
        if(NoteDelivery.folder(this)==null) { status.setText("Choose your sync folder first."); return; }
        Ui.enabled(retry,false); status.setText("Copying saved notes…");
        new Thread(()->{
            int count=0; String error=null;
            for(File note:NoteStorage.notes(this)) {
                try { DeliveryJobs.finishDelivery(this,note); count++; } catch(Exception e) { error=e.getMessage(); break; }
            }
            final String message=error==null?count+" notes available in sync folder. Syncthing handles transfer.":"Copy pending: "+error;
            runOnUiThread(()->{if(isDestroyed()) return; Ui.enabled(retry,true); status.setText(message);});
        },"Autio-delivery").start();
    }
}
