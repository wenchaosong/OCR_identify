package com.ocr;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.WriterException;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.PermissionListener;
import com.zxing.ZxingConfig;
import com.zxing.android.CaptureActivity;
import com.zxing.common.Constant;
import com.zxing.encode.CodeCreator;

import java.util.List;

/**
 * Created by songwenchao
 * on 2018/2/2 0002.
 * <p>
 * 类名
 * 需要 --
 * 可以 --
 */
public class ZXingActivity extends AppCompatActivity implements View.OnClickListener {

    private Button scanBtn;
    private TextView result;
    private EditText contentEt;
    private Button encodeBtn;
    private ImageView contentIv;
    private int REQUEST_CODE_SCAN = 111;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zxing);

        initView();
    }

    private void initView() {
        /*扫描按钮*/
        scanBtn = (Button) findViewById(R.id.scanBtn);
        scanBtn.setOnClickListener(this);
        /*扫描结果*/
        result = (TextView) findViewById(R.id.result);

        /*要生成二维码的输入框*/
        contentEt = (EditText) findViewById(R.id.contentEt);
        /*生成按钮*/
        encodeBtn = (Button) findViewById(R.id.encodeBtn);
        encodeBtn.setOnClickListener(this);
        /*生成的图片*/
        contentIv = (ImageView) findViewById(R.id.contentIv);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.scanBtn:

                AndPermission.with(this)
                        .permission(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
                        .callback(new PermissionListener() {
                            @Override
                            public void onSucceed(int requestCode, @NonNull List<String> grantPermissions) {
                                Intent intent = new Intent(ZXingActivity.this, CaptureActivity.class);

                                /*ZxingConfig是配置类  可以设置是否显示底部布局，闪光灯，相册等动能
                                * 也可以不传这个参数
                                * 不传的话都为true
                                * */
                                ZxingConfig config = new ZxingConfig();
                                intent.putExtra(Constant.INTENT_ZXING_CONFIG, config);

                                startActivityForResult(intent, REQUEST_CODE_SCAN);
                            }

                            @Override
                            public void onFailed(int requestCode, @NonNull List<String> deniedPermissions) {

                                Uri packageURI = Uri.parse("package:" + getPackageName());
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageURI);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                startActivity(intent);

                                Toast.makeText(ZXingActivity.this, "没有权限无法扫描呦", Toast.LENGTH_LONG).show();
                            }
                        }).start();

                break;
            case R.id.encodeBtn:
                String contentEtString = contentEt.getText().toString().trim();
                if (TextUtils.isEmpty(contentEtString)) {
                    Toast.makeText(this, "contentEtString不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                Bitmap bitmap = null;
                try {
                    bitmap = CodeCreator.createQRCode(contentEtString, 400, 400, null);
                } catch (WriterException e) {
                    e.printStackTrace();
                }
                if (bitmap != null) {
                    contentIv.setImageBitmap(bitmap);
                }

                break;

            default:
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 扫描二维码/条码回传
        if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK) {
            if (data != null) {

                String content = data.getStringExtra(Constant.CODED_CONTENT);
                result.setText("扫描结果为：" + content);
            }
        }
    }
}
