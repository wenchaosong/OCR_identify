package com.ocr;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.identify.Youtu;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import cc.cloudist.acplibrary.ACProgressConstant;
import cc.cloudist.acplibrary.ACProgressFlower;
import me.nereo.multi_image_selector.MultiImageSelector;
import me.nereo.multi_image_selector.MultiImageSelectorActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageView imageView;
    private final static int REQUEST_IMAGE = 1;
    private Bitmap bitmap;
    private ACProgressFlower mDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        imageView = (ImageView) findViewById(R.id.image);
        imageView.setOnClickListener(this);

        findViewById(R.id.tv_identify).setOnClickListener(this);

        Youtu.initSDK("10079696", "AKIDKlJvdRYJQUgy1ArUdklr0EXwRrFbdLlC", "w09siGi8Sho061lFbqvpcEfYL0UmvwnS");

        mDialog = new ACProgressFlower.Builder(MainActivity.this)
                .direction(ACProgressConstant.DIRECT_CLOCKWISE)
                .themeColor(Color.WHITE)
                .petalThickness(4)
                .text("解析中")
                .textSize(18)
                .textMarginTop(10)
                .fadeColor(Color.DKGRAY).build();
    }

    /**
     * select picture
     */
    private void selectImage() {
        MultiImageSelector.create(MainActivity.this)
                .showCamera(true) // 是否显示相机. 默认为显示
//                .count(1) // 最大选择图片数量, 默认为9. 只有在选择模式为多选时有效
                .single() // 单选模式
//                .multi() // 多选模式, 默认模式;
//                .origin(ArrayList<String>) // 默认已选择图片. 只有在选择模式为多选时有效
                .start(MainActivity.this, REQUEST_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == RESULT_OK) {
                // 获取返回的图片列表
                List<String> path = data.getStringArrayListExtra(MultiImageSelectorActivity.EXTRA_RESULT);
                // 处理你自己的逻辑 ....
                if (path != null && path.size() > 0) {
                    bitmap = getImage(path.get(0));
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }

    /**
     * 获取压缩后的图片
     */
    private Bitmap getImage(String srcPath) {
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        // 开始读入图片，此时把options.inJustDecodeBounds 设回true了
        newOpts.inJustDecodeBounds = true;
        Bitmap bitmap = BitmapFactory.decodeFile(srcPath, newOpts);// 不可删除,否则会卡顿

        newOpts.inJustDecodeBounds = false;
        int w = newOpts.outWidth;
        int h = newOpts.outHeight;
        // 现在主流手机比较多是800*480分辨率，所以高和宽我们设置为
        float hh = 800f;// 这里设置高度为800f
        float ww = 480f;// 这里设置宽度为480fww
        // 缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
        int be = 1;// be=1表示不缩放
        if (w > h && w > ww) {// 如果宽度大的话根据宽度固定大小缩放
            be = (int) (newOpts.outWidth / ww);
        } else if (w < h && h > hh) {// 如果高度高的话根据宽度固定大小缩放
            be = (int) (newOpts.outHeight / hh);
        }
        if (be <= 0)
            be = 1;
        newOpts.inSampleSize = be;// 设置缩放比例
        // 重新读入图片，注意此时已经把options.inJustDecodeBounds 设回false了
        bitmap = BitmapFactory.decodeFile(srcPath, newOpts);
        return compressImage(bitmap);// 压缩好比例大小后再进行质量压缩
    }

    private Bitmap compressImage(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);// 质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
        int options = 100;
        while (baos.toByteArray().length / 1024 > 100) {  // 循环判断如果压缩后图片是否大于100kb,大于继续压缩
            baos.reset();// 重置baos即清空baos
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);// 这里压缩options%，把压缩后的数据存放到baos中
            options -= 10;// 每次都减少10
        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());// 把压缩后的数据baos存放到ByteArrayInputStream中
        return BitmapFactory.decodeStream(isBm, null, null);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.image:
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    // 应用没有读取手机外部存储的权限
                    // 申请WRITE_EXTERNAL_STORAGE权限
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_IMAGE);
                } else {
                    selectImage();
                }
                break;
            case R.id.tv_identify:// 识别身份证信息
                mDialog.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject jsonObject = Youtu.getInstance().IdcardOcr(bitmap, 0);

                            String name = (String) jsonObject.get("name");
                            String sex = (String) jsonObject.get("sex");
                            String nation = (String) jsonObject.get("nation");
                            String birth = (String) jsonObject.get("birth");
                            String address = (String) jsonObject.get("address");
                            String id = (String) jsonObject.get("id");

                            final StringBuilder sb = new StringBuilder();
                            sb.append("姓名：" + name + "\n");
                            sb.append("性别：" + sex + "\n");
                            sb.append("民族：" + nation + "\n");
                            sb.append("出生：" + birth + "\n");
                            sb.append("住址：" + address + "\n");
                            sb.append("公民身份号码：" + id + "\n");

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mDialog.dismiss();

                                    showDialogInfo(sb.toString());
                                }
                            });
                        } catch (IOException | JSONException | KeyManagementException | NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }

                    }
                }).start();
                break;
        }
    }

    /**
     * 显示对话框
     */
    private void showDialogInfo(String result) {

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        AlertDialog dialogInfo = builder.setTitle("识别成功")
                .setMessage(result)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create();
        dialogInfo.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_IMAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 申请到权限
                selectImage();
            } else {
                Toast.makeText(getApplicationContext(), "没有读取外部存储权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
