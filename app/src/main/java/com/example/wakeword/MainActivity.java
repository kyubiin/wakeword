//package com.example.wakeword;
//
//import android.Manifest;
//import android.annotation.SuppressLint;
//import android.app.Activity;
//import android.content.Context;
//import android.content.pm.PackageManager;
//import android.content.res.AssetFileDescriptor;
//import android.content.res.AssetManager;
//import android.media.AudioFormat;
//import android.media.AudioRecord;
//import android.media.MediaRecorder;
//import android.os.Bundle;
//import android.os.Process;
//import android.util.Log;
//import android.widget.TextView;
//
//import androidx.activity.EdgeToEdge;
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import org.tensorflow.lite.Interpreter;
//
//import java.io.BufferedReader;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.nio.MappedByteBuffer;
//import java.nio.channels.FileChannel;
//import java.util.ArrayDeque;
//import java.util.ArrayList;
//import java.util.List;
//
//public class MainActivity extends AppCompatActivity {
//    // 오디오 녹음 권한 요청 코드
//    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 200;
//
//    // 화면에 결과를 표시할 TextView
//    private TextView melSpecText;  // Mel 스펙트로그램 관련 정보 출력
//    private TextView wakeText;     // wakeword 탐지 결과 출력
//    private TextView yamnetText;   // YAMNet 분류 결과 출력
//
//    // 모델 실행을 위한 객체들
//    private ONNXModelRunner modelRunner;
//    private Model model;                  // 커스텀 ONNX 모델 wrapper
//    private Interpreter yamnetInterpreter; // TensorFlow Lite YAMNet interpreter
//    private List<String> yamnetLabels;    // YAMNet 라벨 목록
//    private AssetManager assetManager;    // assets 폴더 접근용
//
//    // 오디오 녹음을 수행하는 스레드
//    private AudioRecorderThread recorder;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        // 엣지-투-엣지 모드 활성화 (상태바/네비게이션바 투명화)
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_main);
//
//        // 레이아웃에서 TextView 참조
//        melSpecText  = findViewById(R.id.melspec);
//        wakeText     = findViewById(R.id.predicted);
//        yamnetText   = findViewById(R.id.yamnet);
//        assetManager = getAssets();
//
//        try {
//            // ONNX 모델 초기화
//            modelRunner = new ONNXModelRunner(assetManager);
//            model = new Model(modelRunner);
//            // YAMNet 모델과 라벨 불러오기
//            loadYamNetModelAndLabels();
//        } catch (Exception e) {
//            Log.e("Initialization", "Error initializing models", e);
//        }
//
//        // 녹음 스레드 생성
//        recorder = new AudioRecorderThread(
//                this,
//                melSpecText,
//                wakeText,
//                yamnetText,
//                model,
//                yamnetInterpreter,
//                yamnetLabels
//        );
//
//        // 권한 확인 후 녹음 시작
//        if (checkAndRequestPermissions()) {
//            recorder.start();
//        }
//    }
//
//    // RECORD_AUDIO 권한이 있는지 확인하고, 없으면 요청
//    private boolean checkAndRequestPermissions() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(
//                    this,
//                    new String[]{Manifest.permission.RECORD_AUDIO},
//                    PERMISSION_REQUEST_RECORD_AUDIO
//            );
//            return false;
//        }
//        return true;
//    }
//
//    // 권한 요청 결과 처리
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO
//                && grantResults.length > 0
//                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            // 권한 승인 시 녹음 스레드 시작
//            if (recorder != null) {
//                recorder.start();
//            }
//        }
//    }
//
//    // assets 폴더에서 YAMNet TFLite 모델과 라벨 파일을 읽어오는 메서드
//    private void loadYamNetModelAndLabels() throws IOException {
//        // 모델 파일 매핑
//        AssetFileDescriptor fd = assetManager.openFd("yamnet.tflite");
//        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
//        FileChannel fc = fis.getChannel();
//        long start = fd.getStartOffset();
//        long length = fd.getDeclaredLength();
//        MappedByteBuffer modelBuffer = fc.map(FileChannel.MapMode.READ_ONLY, start, length);
//        yamnetInterpreter = new Interpreter(modelBuffer);
//        fis.close();
//
//        // 라벨 파일 읽어서 리스트에 저장
//        yamnetLabels = new ArrayList<>();
//        BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("yamnet_labels.txt")));
//        String line;
//        while ((line = reader.readLine()) != null) {
//            yamnetLabels.add(line);
//        }
//        reader.close();
//    }
//}
//
//// 오디오 녹음 및 모델 추론을 수행하는 스레드 클래스
//class AudioRecorderThread extends Thread {
//    // 오디오 설정 상수
//    private static final int SAMPLE_RATE = 16000;               // 샘플링 레이트 16kHz
//    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; // Mono 입력
//    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // 16bit PCM
//    private static final int CHUNK_SIZE = 1280;                 // 한 번에 읽는 샘플 개수(≈80ms)
//    private static final int YAMNET_LENGTH = 15600;             // YAMNet 입력 길이(≈0.975s)
//
//    private final Context context;
//    private final TextView melSpecText;
//    private final TextView wakeText;
//    private final TextView yamnetText;
//    private final Model model;
//    private final Interpreter yamnetInterpreter;
//    private final List<String> yamnetLabels;
//    // input 오디오 데이터를 저장하는 큐
//    private final ArrayDeque<Float> rawWaveBuffer = new ArrayDeque<>();
//
//    private AudioRecord audioRecord;
//    private boolean isRecording = false;
//
//    AudioRecorderThread(
//            Context context,
//            TextView melSpecText,
//            TextView wakeText,
//            TextView yamnetText,
//            Model model,
//            Interpreter yamnetInterpreter,
//            List<String> yamnetLabels
//    ) {
//        this.context           = context;
//        this.melSpecText       = melSpecText;
//        this.wakeText          = wakeText;
//        this.yamnetText        = yamnetText;
//        this.model             = model;
//        this.yamnetInterpreter = yamnetInterpreter;
//        this.yamnetLabels      = yamnetLabels;
//    }
//
//    @Override
//    @SuppressLint("MissingPermission")
//    public void run() {
//        // 오디오 스레드 우선순위 설정
//        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
//
//        // 최소 버퍼 크기 계산 및 AudioRecord 생성
//        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
//        if (minBufferSize / 2 < CHUNK_SIZE) {
//            minBufferSize = CHUNK_SIZE * 2;
//        }
//        audioRecord = new AudioRecord(
//                MediaRecorder.AudioSource.MIC,
//                SAMPLE_RATE,
//                CHANNEL_CONFIG,
//                AUDIO_FORMAT,
//                minBufferSize
//        );
//        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) return;
//
//        short[] audioBuffer = new short[CHUNK_SIZE];
//        audioRecord.startRecording();
//        isRecording = true;
//
//        // 녹음 및 추론 루프
//        while (isRecording) {
//            // 오디오 데이터를 읽어서 short 배열에 저장
//            audioRecord.read(audioBuffer, 0, audioBuffer.length);
//
//            // short → float 변환 및 rawWaveBuffer에 누적
//            float[] floatBuffer = new float[CHUNK_SIZE];
//            for (int i = 0; i < CHUNK_SIZE; i++) {
//                floatBuffer[i] = audioBuffer[i] / 32768.0f;
//                rawWaveBuffer.offer(floatBuffer[i]);
//            }
//
//            // ONNX 모델로 wakeword 확률 예측
//            final String wakeProb = model.predict_WakeWord(floatBuffer);
//
//            // YAMNet 입력 버퍼가 충분히 쌓이면 분류 수행
//            final String yamnetCategory;
//            if (rawWaveBuffer.size() >= YAMNET_LENGTH) {
//                float[] yamnetInput = new float[YAMNET_LENGTH];
//                for (int i = 0; i < YAMNET_LENGTH; i++) {
//                    yamnetInput[i] = rawWaveBuffer.poll();
//                }
//                float[][] input2D = new float[1][YAMNET_LENGTH];
//                input2D[0] = yamnetInput;
//                float[][] output2D = new float[1][521];
//                yamnetInterpreter.run(input2D, output2D);
//
//                // 가장 높은 확률 인덱스 찾기
//                int maxIdx = 0;
//                for (int i = 1; i < output2D[0].length; i++) {
//                    if (output2D[0][i] > output2D[0][maxIdx]) maxIdx = i;
//                }
//                yamnetCategory = yamnetLabels.get(maxIdx);
//            } else {
//                yamnetCategory = null; // 버퍼 부족 시 분류 건너뜀
//            }
//
//            // UI 업데이트는 메인 스레드에서 수행
//            ((Activity) context).runOnUiThread(() -> {
//                melSpecText.setText("Wakeword Probability: " + wakeProb);
//                // 확률이 0.05 초과 시 탐지 메시지 표시
//                wakeText.setText(Double.parseDouble(wakeProb) > 0.05 ? "Wake Word Detected!" : "");
//                if (yamnetCategory != null) {
//                    yamnetText.setText("YAMNet Category: " + yamnetCategory);
//                }
//            });
//        }
//
//        // 녹음 종료 및 자원 해제
//        audioRecord.stop();
//        audioRecord.release();
//    }
//
//    // 외부에서 녹음을 중지할 때 호출
//    public void stopRecording() {
//        isRecording = false;
//    }
//}

// 2
//package com.example.wakeword;
//
//import android.Manifest;
//import android.annotation.SuppressLint;
//import android.app.Activity;
//import android.content.Context;
//import android.content.pm.PackageManager;
//import android.content.res.AssetFileDescriptor;
//import android.content.res.AssetManager;
//import android.media.AudioFormat;
//import android.media.AudioRecord;
//import android.media.MediaRecorder;
//import android.os.Bundle;
//import android.os.Process;
//import android.util.Log;
//import android.widget.TextView;
//
//import androidx.activity.EdgeToEdge;
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import org.tensorflow.lite.Interpreter;
//
//import java.io.BufferedReader;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.nio.MappedByteBuffer;
//import java.nio.channels.FileChannel;
//import java.util.ArrayDeque;
//import java.util.ArrayList;
//import java.util.List;
//
//public class MainActivity extends AppCompatActivity {
//    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 200;
//
//    private TextView melSpecText;
//    private TextView wakeText;
//    private TextView yamnetText;
//
//    private ONNXModelRunner modelRunner;
//    private Model model;
//    private Interpreter yamnetInterpreter;
//    private List<String> yamnetLabels;
//    private AssetManager assetManager;
//
//    private AudioRecorderThread recorder;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_main);
//
//        melSpecText  = findViewById(R.id.melspec);
//        wakeText     = findViewById(R.id.predicted);
//        yamnetText   = findViewById(R.id.yamnet);
//        assetManager = getAssets();
//
//        try {
//            modelRunner = new ONNXModelRunner(assetManager);
//            model = new Model(modelRunner);
//            loadYamNetModelAndLabels();
//        } catch (Exception e) {
//            Log.e("Initialization", "Error initializing models", e);
//        }
//
//        recorder = new AudioRecorderThread(
//                this,
//                melSpecText,
//                wakeText,
//                yamnetText,
//                model,
//                yamnetInterpreter,
//                yamnetLabels
//        );
//
//        if (checkAndRequestPermissions()) {
//            recorder.start();
//        }
//    }
//
//    private boolean checkAndRequestPermissions() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(
//                    this,
//                    new String[]{Manifest.permission.RECORD_AUDIO},
//                    PERMISSION_REQUEST_RECORD_AUDIO
//            );
//            return false;
//        }
//        return true;
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (recorder != null) {
//            recorder.stopRecording();
//            try {
//                recorder.join();
//            } catch (InterruptedException e) {
//                Log.e("Thread", "Join interrupted", e);
//            }
//        }
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO
//                && grantResults.length > 0
//                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            if (recorder != null) {
//                recorder.start();
//            }
//        }
//    }
//
//    private void loadYamNetModelAndLabels() throws IOException {
//        AssetFileDescriptor fd = assetManager.openFd("yamnet.tflite");
//        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
//        FileChannel fc = fis.getChannel();
//        MappedByteBuffer modelBuffer = fc.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
//        yamnetInterpreter = new Interpreter(modelBuffer);
//        fis.close();
//
//        yamnetLabels = new ArrayList<>();
//        BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("yamnet_labels.txt")));
//        String line;
//        while ((line = reader.readLine()) != null) {
//            yamnetLabels.add(line);
//        }
//        reader.close();
//    }
//}
//
//class AudioRecorderThread extends Thread {
//    private static final int SAMPLE_RATE = 16000;
//    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
//    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
//    private static final int CHUNK_SIZE = 1280;
//    private static final int YAMNET_LENGTH = 15600;
//
//    private final Context context;
//    private final TextView melSpecText;
//    private final TextView wakeText;
//    private final TextView yamnetText;
//    private final Model model;
//    private final Interpreter yamnetInterpreter;
//    private final List<String> yamnetLabels;
//
//    private AudioRecord audioRecord;
//    private volatile boolean isRecording = false;
//
//    private final float[] floatBuffer = new float[CHUNK_SIZE];
//    private final float[] yamnetInput = new float[YAMNET_LENGTH];
//    private final float[][] input2D = new float[1][YAMNET_LENGTH];
//    private final float[][] output2D = new float[1][521];
//    private long lastYamnetTime = 0;
//
//    private final ArrayDeque<Float> rawWaveBuffer = new ArrayDeque<>();
//
//    AudioRecorderThread(
//            Context context,
//            TextView melSpecText,
//            TextView wakeText,
//            TextView yamnetText,
//            Model model,
//            Interpreter yamnetInterpreter,
//            List<String> yamnetLabels
//    ) {
//        this.context = context;
//        this.melSpecText = melSpecText;
//        this.wakeText = wakeText;
//        this.yamnetText = yamnetText;
//        this.model = model;
//        this.yamnetInterpreter = yamnetInterpreter;
//        this.yamnetLabels = yamnetLabels;
//    }
//
//    @Override
//    @SuppressLint("MissingPermission")
//    public void run() {
//        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
//
//        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
//        if (minBufferSize / 2 < CHUNK_SIZE) {
//            minBufferSize = CHUNK_SIZE * 2;
//        }
//        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);
//        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) return;
//
//        short[] tempBuffer = new short[CHUNK_SIZE];
//        audioRecord.startRecording();
//        isRecording = true;
//
//        while (isRecording && audioRecord != null) {
//            int readResult = audioRecord.read(tempBuffer, 0, tempBuffer.length);
//            if (readResult < 0) continue;
//
//            for (int i = 0; i < readResult; i++) {
//                float f = tempBuffer[i] / 32768.0f;
//                floatBuffer[i] = f;
//                rawWaveBuffer.offer(f);
//            }
//
//            final String wakeProb;
//            try {
//                wakeProb = model.predict_WakeWord(floatBuffer);
//            } catch (Exception e) {
//                Log.e("Inference", "Wakeword inference failed", e);
//                continue;
//            }
//
//            final String yamnetCategory;
//            long now = System.currentTimeMillis();
//            if (rawWaveBuffer.size() >= YAMNET_LENGTH && now - lastYamnetTime > 800) {
//                lastYamnetTime = now;
//                for (int i = 0; i < YAMNET_LENGTH; i++) {
//                    yamnetInput[i] = rawWaveBuffer.poll();
//                }
//                input2D[0] = yamnetInput;
//                try {
//                    yamnetInterpreter.run(input2D, output2D);
//                } catch (Exception e) {
//                    Log.e("Inference", "YAMNet inference failed", e);
//                    continue;
//                }
//                int maxIdx = 0;
//                for (int i = 1; i < output2D[0].length; i++) {
//                    if (output2D[0][i] > output2D[0][maxIdx]) maxIdx = i;
//                }
//                yamnetCategory = yamnetLabels.get(maxIdx);
//            } else {
//                yamnetCategory = null;
//            }
//
//            ((Activity) context).runOnUiThread(() -> {
//                melSpecText.setText("Wakeword Probability: " + wakeProb);
//                wakeText.setText(Double.parseDouble(wakeProb) > 0.05 ? "Wake Word Detected!" : "");
//                if (yamnetCategory != null) {
//                    yamnetText.setText("YAMNet Category: " + yamnetCategory);
//                }
//            });
//        }
//
//        if (audioRecord != null) {
//            audioRecord.stop();
//            audioRecord.release();
//            audioRecord = null;
//        }
//    }
//
//    public void stopRecording() {
//        isRecording = false;
//    }
//}
//
//2
//
package com.example.wakeword;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AudioRecorderThread.InferenceCallback {
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 200;

    private TextView melSpecText;
    private TextView wakeText;
    private TextView yamnetText;

    private ONNXModelRunner modelRunner;
    private Model model;
    private Interpreter yamnetInterpreter;
    private List<String> yamnetLabels;
    private AssetManager assetManager;

    private AudioRecorderThread recorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        melSpecText  = findViewById(R.id.melspec);
        wakeText     = findViewById(R.id.predicted);
        yamnetText   = findViewById(R.id.yamnet);
        assetManager = getAssets();

        try {
            modelRunner = new ONNXModelRunner(assetManager);
            model = new Model(modelRunner);
            loadYamNetModelAndLabels();
        } catch (Exception e) {
            Log.e("Initialization", "Error initializing models", e);
        }

        recorder = new AudioRecorderThread(
                this,
                model,
                yamnetInterpreter,
                yamnetLabels,
                this // callback
        );

        if (checkAndRequestPermissions()) {
            recorder.start();
        }
    }

    private boolean checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_RECORD_AUDIO
            );
            return false;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (recorder != null) {
            recorder.stopRecording();
            try {
                recorder.join();
            } catch (InterruptedException e) {
                Log.e("Thread", "Join interrupted", e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (recorder != null) {
                recorder.start();
            }
        }
    }

    private void loadYamNetModelAndLabels() throws IOException {
        AssetFileDescriptor fd = assetManager.openFd("yamnet.tflite");
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel fc = fis.getChannel();
        MappedByteBuffer modelBuffer = fc.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        yamnetInterpreter = new Interpreter(modelBuffer);
        fis.close();

        yamnetLabels = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("yamnet_labels.txt")));
        String line;
        while ((line = reader.readLine()) != null) {
            yamnetLabels.add(line);
        }
        reader.close();
    }

    @Override
    public void onInference(String wakeProb, String yamnetCategory) {
        runOnUiThread(() -> {
            melSpecText.setText("Wakeword Probability: " + wakeProb);
            wakeText.setText(Double.parseDouble(wakeProb) > 0.05 ? "Wake Word Detected!" : "");
            if (yamnetCategory != null) {
                yamnetText.setText("YAMNet Category: " + yamnetCategory);
            }
        });
    }
}

class AudioRecorderThread extends Thread {
    public interface InferenceCallback {
        void onInference(String wakeProb, String yamnetCategory);
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHUNK_SIZE = 1280;
    private static final int YAMNET_LENGTH = 15600;

    private final Model model;
    private final Interpreter yamnetInterpreter;
    private final List<String> yamnetLabels;
    private final InferenceCallback callback;

    private AudioRecord audioRecord;
    private volatile boolean isRecording = false;

    private final float[] floatBuffer = new float[CHUNK_SIZE];
    private final float[] yamnetInput = new float[YAMNET_LENGTH];
    private final float[][] input2D = new float[1][YAMNET_LENGTH];
    private final float[][] output2D = new float[1][521];
    private long lastYamnetTime = 0;

    private final ArrayDeque<Float> rawWaveBuffer = new ArrayDeque<>();

    AudioRecorderThread(
            Context context,
            Model model,
            Interpreter yamnetInterpreter,
            List<String> yamnetLabels,
            InferenceCallback callback
    ) {
        this.model = model;
        this.yamnetInterpreter = yamnetInterpreter;
        this.yamnetLabels = yamnetLabels;
        this.callback = callback;
    }

    @Override
    @SuppressLint("MissingPermission")
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBufferSize / 2 < CHUNK_SIZE) {
            minBufferSize = CHUNK_SIZE * 2;
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) return;

        short[] tempBuffer = new short[CHUNK_SIZE];
        audioRecord.startRecording();
        isRecording = true;

        while (isRecording && audioRecord != null) {
            int readResult = audioRecord.read(tempBuffer, 0, tempBuffer.length);
            if (readResult < 0) continue;

            for (int i = 0; i < readResult; i++) {
                float f = tempBuffer[i] / 32768.0f;
                floatBuffer[i] = f;
                rawWaveBuffer.offer(f);
            }

            String wakeProb;
            try {
                wakeProb = model.predict_WakeWord(floatBuffer);
            } catch (Exception e) {
                Log.e("Inference", "Wakeword inference failed", e);
                continue;
            }

            String yamnetCategory = null;
            long now = System.currentTimeMillis();
            if (rawWaveBuffer.size() >= YAMNET_LENGTH && now - lastYamnetTime > 800) {
                lastYamnetTime = now;
                for (int i = 0; i < YAMNET_LENGTH; i++) {
                    yamnetInput[i] = rawWaveBuffer.poll();
                }
                input2D[0] = yamnetInput;
                try {
                    yamnetInterpreter.run(input2D, output2D);
                } catch (Exception e) {
                    Log.e("Inference", "YAMNet inference failed", e);
                }
                int maxIdx = 0;
                for (int i = 1; i < output2D[0].length; i++) {
                    if (output2D[0][i] > output2D[0][maxIdx]) maxIdx = i;
                }
                yamnetCategory = yamnetLabels.get(maxIdx);
            }

            callback.onInference(wakeProb, yamnetCategory);
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    public void stopRecording() {
        isRecording = false;
    }
}

//package com.example.wakeword;
//
//import android.Manifest;
//import android.annotation.SuppressLint;
//import android.app.Activity;
//import android.content.Context;
//import android.content.pm.PackageManager;
//import android.content.res.AssetFileDescriptor;
//import android.content.res.AssetManager;
//import android.media.AudioFormat;
//import android.media.AudioRecord;
//import android.media.MediaRecorder;
//import android.os.Bundle;
//import android.os.Process;
//import android.util.Log;
//import android.widget.TextView;
//
//import androidx.activity.EdgeToEdge;
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import org.tensorflow.lite.Interpreter;
//
//import java.io.BufferedReader;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.nio.MappedByteBuffer;
//import java.nio.channels.FileChannel;
//import java.util.ArrayDeque;
//import java.util.ArrayList;
//import java.util.List;
//
//public class MainActivity extends AppCompatActivity implements AudioRecorderThread.InferenceCallback {
//    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 200;
//
//    private TextView melSpecText;
//    private TextView wakeText;
//    private TextView yamnetText;
//
//    private ONNXModelRunner modelRunner;
//    private FeatureExtractor featureExtractor;
//    private WakeWordClassifier wakeClassifier;
//    private Interpreter yamnetInterpreter;
//    private List<String> yamnetLabels;
//    private AssetManager assetManager;
//
//    private AudioRecorderThread recorder;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_main);
//
//        melSpecText  = findViewById(R.id.melspec);
//        wakeText     = findViewById(R.id.predicted);
//        yamnetText   = findViewById(R.id.yamnet);
//        assetManager = getAssets();
//
//        try {
//            modelRunner = new ONNXModelRunner(assetManager);
//            featureExtractor = new FeatureExtractor(modelRunner);
//            wakeClassifier   = new WakeWordClassifier(modelRunner);
//            loadYamNetModelAndLabels();
//        } catch (Exception e) {
//            Log.e("Initialization", "Error initializing models", e);
//        }
//
//        recorder = new AudioRecorderThread(
//                featureExtractor,
//                wakeClassifier,
//                yamnetInterpreter,
//                yamnetLabels,
//                this  // callback
//        );
//
//        if (checkAndRequestPermissions()) {
//            recorder.start();
//        }
//    }
//
//    private boolean checkAndRequestPermissions() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(
//                    this,
//                    new String[]{Manifest.permission.RECORD_AUDIO},
//                    PERMISSION_REQUEST_RECORD_AUDIO
//            );
//            return false;
//        }
//        return true;
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (recorder != null) {
//            recorder.stopRecording();
//            try {
//                recorder.join();
//            } catch (InterruptedException e) {
//                Log.e("Thread", "Join interrupted", e);
//            }
//        }
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode,
//                                           @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO
//                && grantResults.length > 0
//                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            if (recorder != null) {
//                recorder.start();
//            }
//        }
//    }
//
//    private void loadYamNetModelAndLabels() throws IOException {
//        AssetFileDescriptor fd = assetManager.openFd("yamnet.tflite");
//        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
//        FileChannel fc = fis.getChannel();
//        MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
//        yamnetInterpreter = new Interpreter(buffer);
//        fis.close();
//
//        yamnetLabels = new ArrayList<>();
//        BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("yamnet_labels.txt")));
//        String line;
//        while ((line = reader.readLine()) != null) {
//            yamnetLabels.add(line);
//        }
//        reader.close();
//    }
//
//    @Override
//    public void onInference(String wakeProb, String yamnetCategory) {
//        runOnUiThread(() -> {
//            melSpecText.setText("Wakeword Probability: " + wakeProb);
//            wakeText.setText(Double.parseDouble(wakeProb) > 0.05 ? "Wake Word Detected!" : "");
//            if (yamnetCategory != null) {
//                yamnetText.setText("YAMNet Category: " + yamnetCategory);
//            }
//        });
//    }
//}
//
//class AudioRecorderThread extends Thread {
//    public interface InferenceCallback {
//        void onInference(String wakeProb, String yamnetCategory);
//    }
//
//    private static final int SAMPLE_RATE = 16000;
//    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
//    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
//    private static final int CHUNK_SIZE = 1280;
//    private static final int YAMNET_LENGTH = 15600;
//
//    private final FeatureExtractor featureExtractor;
//    private final WakeWordClassifier wakeClassifier;
//    private final Interpreter yamnetInterpreter;
//    private final List<String> yamnetLabels;
//    private final InferenceCallback callback;
//
//    private AudioRecord audioRecord;
//    private volatile boolean isRecording = false;
//
//    private final float[] tempBuffer = new float[CHUNK_SIZE];
//    private final float[] yamnetInput = new float[YAMNET_LENGTH];
//    private final float[][] input2D = new float[1][YAMNET_LENGTH];
//    private final float[][] output2D = new float[1][521];
//    private long lastYamnetTime = 0;
//    private final ArrayDeque<Float> rawWaveBuffer = new ArrayDeque<>();
//
//    AudioRecorderThread(
//            FeatureExtractor featureExtractor,
//            WakeWordClassifier wakeClassifier,
//            Interpreter yamnetInterpreter,
//            List<String> yamnetLabels,
//            InferenceCallback callback
//    ) {
//        this.featureExtractor = featureExtractor;
//        this.wakeClassifier = wakeClassifier;
//        this.yamnetInterpreter = yamnetInterpreter;
//        this.yamnetLabels = yamnetLabels;
//        this.callback = callback;
//    }
//
//    @Override
//    @SuppressLint("MissingPermission")
//    public void run() {
//        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
//        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
//        if (minBuf / 2 < CHUNK_SIZE) minBuf = CHUNK_SIZE * 2;
//        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBuf);
//        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) return;
//
//        short[] shortBuf = new short[CHUNK_SIZE];
//        audioRecord.startRecording();
//        isRecording = true;
//
//        while (isRecording && audioRecord != null) {
//            int read = audioRecord.read(shortBuf, 0, shortBuf.length);
//            if (read < 0) continue;
//            for (int i = 0; i < read; i++) {
//                float v = shortBuf[i] / 32768.0f;
//                tempBuffer[i] = v;
//                rawWaveBuffer.offer(v);
//            }
//
//            // Feature extraction
//            float[][][] features = featureExtractor.extract(tempBuffer);
//            String wakeProb = wakeClassifier.classify(features);
//
//            // YAMNet classification
//            String yamnetCat = null;
//            long now = System.currentTimeMillis();
//            if (rawWaveBuffer.size() >= YAMNET_LENGTH && now - lastYamnetTime > 800) {
//                lastYamnetTime = now;
//                for (int i = 0; i < YAMNET_LENGTH; i++) yamnetInput[i] = rawWaveBuffer.poll();
//                input2D[0] = yamnetInput;
//                try { yamnetInterpreter.run(input2D, output2D); } catch (Exception e) { Log.e("Inference","YAMNet failed",e); }
//                int max = 0;
//                for (int i = 1; i < output2D[0].length; i++) if (output2D[0][i] > output2D[0][max]) max = i;
//                yamnetCat = yamnetLabels.get(max);
//            }
//
//            callback.onInference(wakeProb, yamnetCat);
//        }
//
//        if (audioRecord != null) {
//            audioRecord.stop();
//            audioRecord.release();
//            audioRecord = null;
//        }
//    }
//
//    public void stopRecording() {
//        isRecording = false;
//    }
//}
