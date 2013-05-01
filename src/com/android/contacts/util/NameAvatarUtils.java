
package com.android.contacts.util;

import com.android.contacts.R;
import com.android.contacts.util.NameAvatarUtils.AvatarRequest;

import java.io.ByteArrayOutputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.FontMetrics;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup.LayoutParams;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.TextView;

public class NameAvatarUtils {
    
    private static int  PX_TEXT_SIZE = 0;
    private static int  PX_PHOTO_SIZE = 0;
    private static final int SP_TEXT_SIZE = 16;
    private static final int DP_PHOTO_SIZE = 64;

    /**
     * Judgment for a character is chinese or not
     * 
     * @author Wang
     * @param a char
     * @return boolean
     * @date 2012-11-01
     */
    public static boolean isChinese(char a) {
        // int v = (int)a;
        // return (v >=19968 && v <= 171941);
        boolean isChina = String.valueOf(a).matches("[\u4E00-\u9FA5]");
        return isChina;
    }

    /**
     * Judgment for the given string contain chinese or not ,and return the last
     * chinese character (null stand for there is no chinese character in this
     * string)
     * 
     * @author Wang
     * @param a String
     * @return the last chinese character
     * @date 2012-10-10
     */
    public static String containsChinese(String str) {
        if (TextUtils.isEmpty(str))
            return null;
        int length = str.length();
        for (int i = length - 1; i >= 0; i--) {
            if (isChinese(str.charAt(i))) {
                String chinese = str.substring(i, i + 1);
                return chinese;
            }
        }
        return null;
    }

    /**
     * Make a name avatar and save it.
     * @author Wang
     * @date 2012-11-14
     * */
    public static boolean setupNameAvatar(ImageView view, long contactID, String displayName){
        String cnCharacter = NameAvatarUtils.containsChinese(displayName);
        if(!TextUtils.isEmpty(cnCharacter)){
            setAvatar(view, cnCharacter, contactID);
            return true;
        }else{
            if(!TextUtils.isEmpty(displayName)){
                setAvatar(view, displayName.substring(0, 1).toUpperCase(), contactID);
                return true;
            }else{
                //Wang:2012-11-13
//                NameAvatarUtils.setAvatar(view, " ", contactID);
            	//Wang:2012-11-14
            	return false;
            }
        }
    }
    
    /**
     * 
     * @author Wang
     * @date 2012-11-15
     * */
    public static Bitmap makeNameAvatarBitmap(Context ctx, String displayName){
        String cnCharacter = NameAvatarUtils.containsChinese(displayName);
        if(!TextUtils.isEmpty(cnCharacter)){
            return makeAvatarBitmap(ctx, cnCharacter);
        }else{
            if(!TextUtils.isEmpty(displayName)){
                return makeAvatarBitmap(ctx, displayName.substring(0, 1).toUpperCase());
            }else{
                return null;
            }
        }
    }
    
    /**
     * Draw the view into a bitmap.
     * 
     * @author Wang
     * @date 2012-10-10
     */
    private static Bitmap getViewBitmap(View v) {
        v.clearFocus();
        v.setPressed(false);

        boolean willNotCache = v.willNotCacheDrawing();
        v.setWillNotCacheDrawing(false);

        // Reset the drawing cache background color to fully transparent
        // for the duration of this operation
        int color = v.getDrawingCacheBackgroundColor();
        v.setDrawingCacheBackgroundColor(0);
        float alpha = v.getAlpha();
        v.setAlpha(1.0f);

        if (color != 0) {
            v.destroyDrawingCache();
        }
        v.setDrawingCacheEnabled(true);
        v.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        v.buildDrawingCache();
        Bitmap cacheBitmap = v.getDrawingCache();
        if (cacheBitmap == null) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(cacheBitmap);

        // Restore the view
        v.destroyDrawingCache();
        v.setAlpha(alpha);
        v.setWillNotCacheDrawing(willNotCache);
        v.setDrawingCacheBackgroundColor(color);

        return bitmap;
    }

    /**
     * Set the charater in the imageview.
     * 
     * @author Wang
     * @date 2012-10-29
     * @date 2012-11-12
     */
    public static void setAvatar(ImageView view, String chara, long contactID) {
        Bitmap bm = makeAvatarBitmap(view.getContext(), chara);
        if (bm != null) {
            if(view != null) view.setImageBitmap(bm);
//            AvatarRequest request = new AvatarRequest(contactID, bm, chara);
//            new AvatarTask(view.getContext()).execute(request);
        }

    }
    
    /**
     * Make a bitmap with given content
     * @author Wang
     * @date 2012-11-14
     * @date 2012-11-16
     * */
    public static Bitmap makeAvatarBitmap(Context ctx, String content){
//        ensureSizeParams(ctx);
//        TextView tv = new TextView(ctx);
        TextView tv = (TextView) LayoutInflater.from(ctx).inflate(
                R.layout.shendu_name_avatar_view, null);
//        tv.setLayoutParams(new LayoutParams(PX_PHOTO_SIZE, PX_PHOTO_SIZE));
//        tv.setGravity(Gravity.CENTER_VERTICAL);
//        tv.setMinimumHeight(PX_PHOTO_SIZE);
//        tv.setMinimumWidth(PX_PHOTO_SIZE);
        int paddingLeft = measurePadding(ctx, content);
        tv.setPadding(paddingLeft, 0, 0, 0);
//        tv.setSingleLine(true);
//        tv.setBackgroundResource(R.color.shendu_name_avatar_background);
//        tv.setTextColor(ctx.getResources().getColor(R.color.shendu_name_avatar_text_color));
//        tv.setTextSize(PX_TEXT_SIZE);
        tv.setText(content);
       return getViewBitmap(tv);
    }
    
    public static int dip2px(int dipValue, float scale) {
        return (int) (dipValue * scale + 0.5f);
    }
    
    private static int sp2px(float spValue, float fontScale) {
        return  (int) (spValue * fontScale + 0.5f);
     }
    /**
     * @author Wang
     * @date 2012-11-16
     * */
    private static void ensureSizeParams(Context ctx){
        if(PX_TEXT_SIZE == 0 || PX_PHOTO_SIZE == 0){
            PX_TEXT_SIZE = ctx.getResources().getDimensionPixelSize(R.dimen.shendu_name_avatar_text_size);
            PX_PHOTO_SIZE = ctx.getResources().getDimensionPixelSize(R.dimen.shendu_name_avatar_size);
        }
    }
    /**
     * @author Wang
     * @date 2012-11-14
     * */
    private static int measurePadding(Context ctx,String str){
        //ensure px values
        ensureSizeParams(ctx);
        Paint p = new Paint();
        p.setTextSize(PX_TEXT_SIZE);
        float font = getFontlength(p, str);
        return (int)(PX_PHOTO_SIZE - font) / 2;
    }

    /**
     * Save Avatar into Database
     * 
     * @author Wang
     * @date 2012-11-12
     */
    private static boolean saveContact(ContentResolver cr, byte[] bytes, long contactId,
            boolean Sync) {
        Cursor c = cr.query(RawContacts.CONTENT_URI, new String[] {
                RawContacts._ID
        }, RawContacts.CONTACT_ID + " = ?", new String[] {
                String.valueOf(contactId)
        }, null);
        long raw_id = -1;
        if (c != null && c.moveToFirst()) {
            raw_id = c.getLong(0);
        }
        c.close();
        if (raw_id == -1) {
            Log.e("shenduContacts", "raw_id = -1");
            return false;
        }
        ContentValues values = new ContentValues();
        Uri uri = Uri.parse("content://com.android.contacts/data");
        int photoRow = -1;
        String where = "raw_contact_id = " +
                raw_id + " AND mimetype  ='vnd.android.cursor.item/photo'";
        Cursor cursor = cr.query(uri, null, where, null, null);
        int idIdx = cursor.getColumnIndexOrThrow("_id");
        if (cursor.moveToFirst()) {
            photoRow = cursor.getInt(idIdx);
        }
        cursor.close();
        values.put("raw_contact_id", raw_id);
        values.put("is_super_primary", 1);
        values.put("data15", bytes);
        values.put("mimetype", "vnd.android.cursor.item/photo");
        int rowInt = -1;
        Uri rowUri = null;
        if (photoRow >= 0) {
            rowInt = cr.update(uri, values, " _id= " + photoRow, null);
        } else {
            rowUri = cr.insert(uri, values);
        }
        if (!Sync) {
            uri = Uri.withAppendedPath(Uri.parse("content://com.android.contacts/raw_contacts"),
                    String.valueOf(raw_id));
            values = new ContentValues();
            values.put("dirty", 0);
            cr.update(uri, values, null, null);
        }
        return rowInt != -1 || rowUri != null;
    }

    static class AvatarRequest {
        long contactID;
        ImageView view;
        String name;
        Bitmap bmp;

        public AvatarRequest(long contactID, Bitmap bmp, String name) {
            this.contactID = contactID;
            this.bmp = bmp;
            this.name = name;
        }
    }

    static class AvatarTask extends AsyncTask<AvatarRequest, Integer, AvatarRequest> {
        private Context ctx;

        public AvatarTask(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        protected AvatarRequest doInBackground(AvatarRequest... params) {
            AvatarRequest request = params[0];
            if (request.contactID != -1) {
                byte[] avatar = convertBmp(request.bmp);
                boolean result = false;
                if (ctx != null) {
                    result = saveContact(ctx.getContentResolver(), avatar, request.contactID, false);
                } else {
                    Log.e("shenduContacts", "saveContact context is NULL");
                }
            }
            return request;
        }

        private byte[] convertBmp(Bitmap bmp) {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            byte[] avatar = os.toByteArray();
            return avatar;
        }

    }

    private static final int AVATAR_SIZE = 128;
    private static final int TEXT_SIZE = AVATAR_SIZE / 2;
    public static WeakReference<Bitmap> makeAvatar(Context ctx, String content) {
        try {
            Bitmap tmpBmp = Bitmap.createBitmap(AVATAR_SIZE, AVATAR_SIZE, Config.ARGB_8888);
            Canvas canvas = new Canvas(tmpBmp);
            canvas.drawColor(ctx.getResources().getColor(R.color.shendu_name_avatar_background));
            Paint p = new Paint();
            p.setColor(ctx.getResources().getColor(R.color.shendu_name_avatar_text_color));
            p.setTextSize(TEXT_SIZE);
            float tX = (AVATAR_SIZE - getFontlength(p, content)) / 2;
            float tY = (AVATAR_SIZE - getFontHeight(p)) / 2 + getFontLeading(p);
            canvas.drawText(content, tX, tY, p);
            return new WeakReference<Bitmap>(tmpBmp);
        } catch (OutOfMemoryError e) {
            return new WeakReference<Bitmap>(null);
        }
    }
    
    public static float getFontlength(Paint paint, String str) {  
        return paint.measureText(str);  
    }
    
    public static float getFontHeight(Paint paint)  {    
        FontMetrics fm = paint.getFontMetrics();   
        return fm.descent - fm.ascent;    
    } 
    
    public static float getFontLeading(Paint paint)  {    
        FontMetrics fm = paint.getFontMetrics();   
        return fm.leading- fm.ascent;    
    }
   
}
