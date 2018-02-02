package com.ocr;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.ocr.sdk.OCR;
import com.baidu.ocr.sdk.OnResultListener;
import com.baidu.ocr.sdk.exception.OCRError;
import com.baidu.ocr.sdk.model.AccessToken;
import com.baidu.ocr.sdk.model.BankCardParams;
import com.baidu.ocr.sdk.model.BankCardResult;
import com.baidu.ocr.sdk.model.IDCardParams;
import com.baidu.ocr.sdk.model.IDCardResult;
import com.baidu.ocr.sdk.model.OcrRequestParams;
import com.baidu.ocr.sdk.model.OcrResponseResult;
import com.baidu.ocr.ui.camera.CameraActivity;
import com.baidu.ocr.ui.camera.CameraNativeHelper;
import com.baidu.ocr.ui.camera.CameraView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_CAMERA = 102;
    private static final int REQUEST_CODE_DRIVING_LICENSE = 103;
    private static final int REQUEST_CODE_VEHICLE_LICENSE = 104;
    private TextView mContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContent = (TextView) findViewById(R.id.content);

        // 正面(手动)
        findViewById(R.id.id_card_front_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_FRONT);
                startActivityForResult(intent, REQUEST_CODE_CAMERA);
            }
        });

        // 反面(手动)
        findViewById(R.id.id_card_back_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_BACK);
                startActivityForResult(intent, REQUEST_CODE_CAMERA);
            }
        });

        // 正面(自动)
        findViewById(R.id.id_card_front_button_auto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_NATIVE_ENABLE, true);
                // KEY_NATIVE_MANUAL设置了之后CameraActivity中不再自动初始化和释放模型
                // 请手动使用CameraNativeHelper初始化和释放模型
                // 推荐这样做，可以避免一些activity切换导致的不必要的异常
                intent.putExtra(CameraActivity.KEY_NATIVE_MANUAL, true);
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_FRONT);
                startActivityForResult(intent, REQUEST_CODE_CAMERA);
            }
        });

        // 反面(自动)
        findViewById(R.id.id_card_back_button_auto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_NATIVE_ENABLE, true);
                // KEY_NATIVE_MANUAL设置了之后CameraActivity中不再自动初始化和释放模型
                // 请手动使用CameraNativeHelper初始化和释放模型
                // 推荐这样做，可以避免一些activity切换导致的不必要的异常
                intent.putExtra(CameraActivity.KEY_NATIVE_MANUAL, true);
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_BACK);
                startActivityForResult(intent, REQUEST_CODE_CAMERA);
            }
        });

        // 银行卡
        findViewById(R.id.credit_card_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_BANK_CARD);
                startActivityForResult(intent, REQUEST_CODE_CAMERA);
            }
        });

        // 驾驶证
        findViewById(R.id.driving_card_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE,
                        CameraActivity.CONTENT_TYPE_GENERAL);
                startActivityForResult(intent, REQUEST_CODE_DRIVING_LICENSE);
            }
        });

        // 行驶证
        findViewById(R.id.vehicle_card_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE,
                        CameraActivity.CONTENT_TYPE_GENERAL);
                startActivityForResult(intent, REQUEST_CODE_VEHICLE_LICENSE);
            }
        });

        // 初始化
        initAccessTokenWithAkSk();
    }

    private void initAccessTokenWithAkSk() {
        OCR.getInstance().initAccessTokenWithAkSk(
                new OnResultListener<AccessToken>() {
                    @Override
                    public void onResult(AccessToken result) {

                        // 本地自动识别需要初始化
                        initLicense();

                        Log.d("MainActivity", "onResult: " + result.toString());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "初始化认证成功", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(OCRError error) {
                        error.printStackTrace();
                        Log.e("MainActivity", "onError: " + error.getMessage());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "初始化认证失败,请检查 key", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }, getApplicationContext(),
                // 需要自己配置 https://console.bce.baidu.com
                "oH6tqEsBX2PSW2OViQyd2yYA",
                // 需要自己配置 https://console.bce.baidu.com
                "A36f7UrseglvtH9jGP5u7bU9uGxIjZ31");
    }

    private void initLicense() {
        CameraNativeHelper.init(this, OCR.getInstance().getLicense(),
                new CameraNativeHelper.CameraNativeInitCallback() {
                    @Override
                    public void onError(int errorCode, Throwable e) {
                        final String msg;
                        switch (errorCode) {
                            case CameraView.NATIVE_SOLOAD_FAIL:
                                msg = "加载so失败，请确保apk中存在ui部分的so";
                                break;
                            case CameraView.NATIVE_AUTH_FAIL:
                                msg = "授权本地质量控制token获取失败";
                                break;
                            case CameraView.NATIVE_INIT_FAIL:
                                msg = "本地质量控制";
                                break;
                            default:
                                msg = String.valueOf(errorCode);
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this,
                                        "本地质量控制初始化错误，错误原因： " + msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CAMERA && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                String contentType = data.getStringExtra(CameraActivity.KEY_CONTENT_TYPE);
                String filePath = FileUtil.getSaveFile(getApplicationContext()).getAbsolutePath();
                if (!TextUtils.isEmpty(contentType)) {
                    if (CameraActivity.CONTENT_TYPE_ID_CARD_FRONT.equals(contentType)) {
                        recIDCard(IDCardParams.ID_CARD_SIDE_FRONT, filePath);
                    } else if (CameraActivity.CONTENT_TYPE_ID_CARD_BACK.equals(contentType)) {
                        recIDCard(IDCardParams.ID_CARD_SIDE_BACK, filePath);
                    } else if (CameraActivity.CONTENT_TYPE_BANK_CARD.equals(contentType)) {
                        recCreditCard(filePath);
                    }
                }
            }
        }
        if (requestCode == REQUEST_CODE_DRIVING_LICENSE && resultCode == Activity.RESULT_OK) {
            String filePath = FileUtil.getSaveFile(getApplicationContext()).getAbsolutePath();
            recDrivingCard(filePath);
        }
        if (requestCode == REQUEST_CODE_VEHICLE_LICENSE && resultCode == Activity.RESULT_OK) {
            String filePath = FileUtil.getSaveFile(getApplicationContext()).getAbsolutePath();
            recVehicleCard(filePath);
        }
    }

    /**
     * 解析身份证图片
     *
     * @param idCardSide 身份证正反面
     * @param filePath   图片路径
     */
    private void recIDCard(String idCardSide, String filePath) {
        IDCardParams param = new IDCardParams();
        param.setImageFile(new File(filePath));
        // 设置身份证正反面
        param.setIdCardSide(idCardSide);
        // 设置方向检测
        param.setDetectDirection(true);
        // 设置图像参数压缩质量0-100, 越大图像质量越好但是请求时间越长。 不设置则默认值为20
        param.setImageQuality(40);
        OCR.getInstance().recognizeIDCard(param, new OnResultListener<IDCardResult>() {
            @Override
            public void onResult(IDCardResult result) {
                if (result != null) {

                    String name = "";
                    String sex = "";
                    String nation = "";
                    String num = "";
                    String address = "";
                    if (result.getName() != null) {
                        name = result.getName().toString();
                    }
                    if (result.getGender() != null) {
                        sex = result.getGender().toString();
                    }
                    if (result.getEthnic() != null) {
                        nation = result.getEthnic().toString();
                    }
                    if (result.getIdNumber() != null) {
                        num = result.getIdNumber().toString();
                    }
                    if (result.getAddress() != null) {
                        address = result.getAddress().toString();
                    }
                    mContent.setText("姓名: " + name + "\n" +
                            "性别: " + sex + "\n" +
                            "民族: " + nation + "\n" +
                            "身份证号码: " + num + "\n" +
                            "住址: " + address + "\n");
                }
            }

            @Override
            public void onError(OCRError error) {
                Toast.makeText(MainActivity.this, "识别出错,请查看log错误代码", Toast.LENGTH_SHORT).show();
                Log.d("MainActivity", "onError: " + error.getMessage());
            }
        });
    }

    /**
     * 解析银行卡
     *
     * @param filePath 图片路径
     */
    private void recCreditCard(String filePath) {
        // 银行卡识别参数设置
        BankCardParams param = new BankCardParams();
        param.setImageFile(new File(filePath));

        // 调用银行卡识别服务
        OCR.getInstance().recognizeBankCard(param, new OnResultListener<BankCardResult>() {
            @Override
            public void onResult(BankCardResult result) {
                if (result != null) {

                    String type;
                    if (result.getBankCardType() == BankCardResult.BankCardType.Credit) {
                        type = "信用卡";
                    } else if (result.getBankCardType() == BankCardResult.BankCardType.Debit) {
                        type = "借记卡";
                    } else {
                        type = "不能识别";
                    }
                    mContent.setText("银行卡号: " + (!TextUtils.isEmpty(result.getBankCardNumber()) ? result.getBankCardNumber() : "") + "\n" +
                            "银行名称: " + (!TextUtils.isEmpty(result.getBankName()) ? result.getBankName() : "") + "\n" +
                            "银行类型: " + type + "\n");
                }
            }

            @Override
            public void onError(OCRError error) {
                Toast.makeText(MainActivity.this, "识别出错,请查看log错误代码", Toast.LENGTH_SHORT).show();
                Log.d("MainActivity", "onError: " + error.getMessage());
            }
        });
    }

    /**
     * 解析驾驶证
     *
     * @param filePath 图片路径
     */
    private void recDrivingCard(String filePath) {
        // 驾驶证识别参数设置
        OcrRequestParams param = new OcrRequestParams();

        // 设置image参数
        param.setImageFile(new File(filePath));
        // 设置其他参数
        param.putParam("detect_direction", true);
        // 调用驾驶证识别服务
        OCR.getInstance().recognizeDrivingLicense(param, new OnResultListener<OcrResponseResult>() {
            @Override
            public void onResult(OcrResponseResult result) {
                // 调用成功，返回OcrResponseResult对象
                Log.d("MainActivity", result.getJsonRes());
                mContent.setText(result.getJsonRes());
            }

            @Override
            public void onError(OCRError error) {
                // 调用失败，返回OCRError对象
            }
        });
    }

    /**
     * 解析行驶证
     *
     * @param filePath 图片路径
     */
    private void recVehicleCard(String filePath) {
        OcrRequestParams param = new OcrRequestParams();
        param.setImageFile(new File(filePath));
        OCR.getInstance().recognizeVehicleLicense(param, new OnResultListener<OcrResponseResult>() {
            @Override
            public void onResult(OcrResponseResult result) {
                Log.d("MainActivity", result.getJsonRes());
                mContent.setText(result.getJsonRes());
            }

            @Override
            public void onError(OCRError error) {
                // 调用失败，返回OCRError对象

            }
        });
    }

    @Override
    protected void onDestroy() {
        CameraNativeHelper.release();
        // 释放内存资源
        OCR.getInstance().release();
        super.onDestroy();

    }
}
