package com.swuniv.agefree.presentation.detection;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.swuniv.agefree.BuildConfig;
import com.swuniv.agefree.R;
import com.swuniv.agefree.presentation.detection.data.model.FaceDetectResponse;
import com.swuniv.agefree.presentation.detection.data.network.RetrofitBuilder;
import com.swuniv.agefree.presentation.detection.utils.PreferenceManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class FdActivity extends CameraActivity implements CvCameraViewListener2 {

    private static final String TAG = "OCVSample::Activity";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255); // 얼굴 감지 사각형 색상
    public static final int JAVA_DETECTOR = 0;
    public static final int NATIVE_DETECTOR = 1;

    private MenuItem mItemFace50;
    private MenuItem mItemFace40;
    private MenuItem mItemFace30;
    private MenuItem mItemFace20;
    private MenuItem mItemType;

    private Mat mRgba;
    private Mat mGray;
    private File mCascadeFile;
    private CascadeClassifier mJavaDetector;
    private DetectionBasedTracker mNativeDetector;

    private int mDetectorType = JAVA_DETECTOR;
    private String[] mDetectorName;

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;
    private boolean isLocked = false;

    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("detection_based_tracker");

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);

                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public FdActivity() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";
        mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_view);


        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(0); // 카메라 전면/후면 0 : 전면, 1 : 후면

        String title = ((TextView) findViewById(R.id.title)).getText().toString();
        SpannableStringBuilder ssb = new SpannableStringBuilder(title);
        ssb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.deepPurple)), 0, 10, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(R.id.title)).setText(ssb);

    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        // 현재 서버로 이미지 전송중이 아닌 경우

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        // OpenCV RGB색상 적용 코드
        if (!mRgba.empty()) {
            Mat convertedMat = new Mat();
            Imgproc.cvtColor(mRgba, convertedMat, Imgproc.COLOR_RGB2BGR);
            mRgba = convertedMat;
        }

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
            mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
        }

        MatOfRect faces = new MatOfRect();

        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetector != null)
                // scaleFactor -> 이미지 크기를 1/(scaleFactor)^n 씩 축소함. (= scaleFactor 가 커질수록 이미지가 한눈에 들어옴)
                // minNeighbors -> 여러 스케일 이미지에서 최소 몇번이나 검출되어야 실제로 유효한 결과라고 판단할지 정하는 값
                mJavaDetector.detectMultiScale(mGray, faces, 1.2, 3, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        } else if (mDetectorType == NATIVE_DETECTOR) {
            if (mNativeDetector != null)
                mNativeDetector.detect(mGray, faces);
        } else {
            Log.e(TAG, "Detection method is not selected!");
        }

        Rect[] facesArray = faces.toArray();

        // Boundary Effect 임시 제거
//        for (int i = 0; i < facesArray.length; i++) {
//            Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);
//        }


        if (!isLocked) {
            // 감지된 얼굴이 1개 이상이면
            if (facesArray.length > 0) {
                // 더 이상 얼굴 감지 못하도록 Lock!
                isLocked = true;

                runOnUiThread(() -> {

                    if (isLocked) {
                        // 스캔 화면 보여주기
                        //((LottieAnimationView) findViewById(R.id.scan_animation_view)).setVisibility(View.VISIBLE);
                        ((TextView) findViewById(R.id.close_come_text_view)).setVisibility(View.GONE);
                    }

                    // 감지된 화면을 bitmap으로 변환
                    Bitmap detectedBitmap = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(mRgba, detectedBitmap);

                    // 테스트로 ImageView에 감지된 사진을 띄우도록 해놨습니다.
                    // 추후 테스트 완료시 JavaCameraView 크기를 match_parent로 해주세요.
                    // Glide.with(this).load(detectedBitmap).into((ImageView) findViewById(R.id.my_iv));
                    //Toast.makeText(this, detectedBitmap.toString(), Toast.LENGTH_SHORT).show();


                    /*
                     * 서버로 Bitmap 전송할 부분
                     * flow : 전송 완료 후, 인식 실패시 감지 재게 / 인식 성공시 다음 화면 전환
                     * (ProgressBar로 대기)
                     * */
                    postBitmap(detectedBitmap);
//                  Toast.makeText(this, "얼굴인식 1개 이상 인식", Toast.LENGTH_SHORT).show();
                });
            }
        }

        return mRgba;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemFace50 = menu.add("Face size 50%");
        mItemFace40 = menu.add("Face size 40%");
        mItemFace30 = menu.add("Face size 30%");
        mItemFace20 = menu.add("Face size 20%");
        mItemType = menu.add(mDetectorName[mDetectorType]);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == mItemFace50)
            setMinFaceSize(0.5f);
        else if (item == mItemFace40)
            setMinFaceSize(0.4f);
        else if (item == mItemFace30)
            setMinFaceSize(0.3f);
        else if (item == mItemFace20)
            setMinFaceSize(0.2f);
        else if (item == mItemType) {
            int tmpDetectorType = (mDetectorType + 1) % mDetectorName.length;
            item.setTitle(mDetectorName[tmpDetectorType]);
            setDetectorType(tmpDetectorType);
        }
        return true;
    }

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }

    private void setDetectorType(int type) {
        if (mDetectorType != type) {
            mDetectorType = type;

            if (type == NATIVE_DETECTOR) {
                Log.i(TAG, "Detection Based Tracker enabled");
                mNativeDetector.start();
            } else {
                Log.i(TAG, "Cascade detector enabled");
                mNativeDetector.stop();
            }
        }
    }

    // 서버 전송 로직 구현
    private void postBitmap(Bitmap detectedBitmap) {
        // 이 부분에 Retrofit으로 RestAPI통신 구현 예정
        //ExtensionsKt.toFile(detectedBitmap, detectedBitmap, "detectedFace");
        File faceFile = convertBitmapToFile(detectedBitmap);
        String multiPartQuery = "image";
        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), faceFile);
        MultipartBody.Part fileBody = MultipartBody.Part.createFormData(multiPartQuery, faceFile.getName(), requestFile);


        Call<FaceDetectResponse> faceRequestCall = RetrofitBuilder.INSTANCE.getFaceDetectApi().validateFaceAge(fileBody);
        faceRequestCall.enqueue(new Callback<FaceDetectResponse>() {
            @Override
            public void onResponse(Call<FaceDetectResponse> call, Response<FaceDetectResponse> response) {
                Log.d(TAG, "onResponse: " + response);
                if (response.isSuccessful()) {
                    // 나이 추정 완료시 완료 화면 VISIBLE!
                    FaceDetectResponse faceDetectResponse = response.body();
                    int age = faceDetectResponse.getAge();
                    String gender = faceDetectResponse.getGender();

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "onResponse: " + faceDetectResponse.getSuccess());
                    }

                    if (faceDetectResponse.getSuccess()) {
                        //((LottieAnimationView) findViewById(R.id.scan_container).setVisibility(View.VISIBLE));
                        findViewById(R.id.scan_container).setVisibility(View.GONE);
                        findViewById(R.id.complete_container).setVisibility(View.VISIBLE);

                        PreferenceManager.INSTANCE.setInt(FdActivity.this, PreferenceManager.ageKey, age);
                        PreferenceManager.INSTANCE.setString(FdActivity.this, PreferenceManager.genderKey, gender);

                        findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);

                        startActivity(new Intent(FdActivity.this, MainActivity.class).putExtra("age", age).putExtra("gender", gender));
                        finish();

                    }
                } else {
                    // 전송을 완료하고 나이 추정 실패시 True로 갱신하여 다시 얼굴인식 시도
                    isLocked = false;
                    // ProgressBar hide!
                    //findViewById(R.id.scan_animation_view).setVisibility(View.GONE);
                    ((TextView) findViewById(R.id.close_come_text_view)).setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<FaceDetectResponse> call, Throwable t) {
                Log.d(TAG, "onFailure: " + t.getMessage());

                // 전송을 완료하고 나이 추정 실패시 True로 갱신하여 다시 얼굴인식 시도
                isLocked = false;
                // ProgressBar hide!
                //((LottieAnimationView) findViewById(R.id.scan_animation_view).setVisibility(View.GONE));
                ((TextView) findViewById(R.id.close_come_text_view)).setVisibility(View.VISIBLE);
            }
        });

    }


    private File convertBitmapToFile(Bitmap bitmap) {
        //create a file to write bitmap data
        File file = new File(getBaseContext().getCacheDir(), "face");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

//Convert bitmap to byte array
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90 /*ignored for PNG*/, bos);
        byte[] bitmapdata = bos.toByteArray();

//write the bytes in file
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            fos.write(bitmapdata);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return file;
    }
}
