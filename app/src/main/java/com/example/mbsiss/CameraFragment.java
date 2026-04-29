package com.example.mbsiss;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;
import android.content.pm.PackageManager;

public class CameraFragment extends Fragment {

    private static final String TAG = "CameraFragment";
    private static final int PERMISSION_REQUEST_CODE = 100;

    // Blur threshold — lower = stricter
    private static final double BLUR_THRESHOLD = 80.0;

    // ── Yellow mask tuning ────────────────────────────────────────────────────
    // Matches the bright yellow highlighter painted on the ID scan-skip zones.
    private static final int YELLOW_R_MIN    = 180;
    private static final int YELLOW_G_MIN    = 180;
    private static final int YELLOW_B_MAX    = 80;
    private static final int YELLOW_MIN_AREA = 2000;

    // ── Registrar zone spatial mask (back ID only) ────────────────────────────
    // The registrar/stamp zone on the back ID sits between the personal contact
    // row and the emergency section. Based on the physical card layout it
    // occupies roughly the middle 25-55% of the card's vertical height.
    // These are proportional values (0.0–1.0) applied to bitmap height.
    //
    // Visual layout of back ID (top=0, bottom=1.0):
    //   0.00 – 0.25  →  Address / Contact No. fields
    //   0.25 – 0.55  →  Registrar zone (disclaimer + stamp + signature) ← MASK
    //   0.55 – 1.00  →  Emergency contact fields
    //
    // Adjust these if testing shows the mask cuts into real data.
    private static final float REGISTRAR_ZONE_TOP    = 0.25f;
    private static final float REGISTRAR_ZONE_BOTTOM = 0.55f;

    // ── UI ────────────────────────────────────────────────────────────────────
    private PreviewView previewView;
    private ImageView   imgFrontPreview, imgBackPreview;
    private Button      btnCapture, btnConfirm;
    private Button      btnRetakeFront, btnRetakeBack;
    private ImageButton btnFlash, btnFlipCamera;
    private TextView    tvInstruction;
    private ProgressBar progressBar;

    // ── Camera ────────────────────────────────────────────────────────────────
    private ProcessCameraProvider cameraProvider;
    private ImageCapture          imageCapture;
    private Camera                camera;
    private boolean               isFlashOn  = false;
    private int                   lensFacing = CameraSelector.LENS_FACING_BACK;
    private ExecutorService       cameraExecutor;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean     frontCaptured = false;
    private boolean     backCaptured  = false;
    private StudentData studentData   = new StudentData();

    public CameraFragment() {}

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);

        previewView     = view.findViewById(R.id.previewView);
        imgFrontPreview = view.findViewById(R.id.imgFrontPreview);
        imgBackPreview  = view.findViewById(R.id.imgBackPreview);
        btnCapture      = view.findViewById(R.id.btnCapture);
        btnConfirm      = view.findViewById(R.id.btnConfirm);
        btnFlash        = view.findViewById(R.id.btnFlash);
        btnFlipCamera   = view.findViewById(R.id.btnFlipCamera);
        tvInstruction   = view.findViewById(R.id.tvInstruction);
        progressBar     = view.findViewById(R.id.progressBar);
        btnRetakeFront  = view.findViewById(R.id.btnRetakeFront);
        btnRetakeBack   = view.findViewById(R.id.btnRetakeBack);

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (hasCameraPermission()) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CODE);
        }

        btnCapture.setOnClickListener(v -> captureImage());
        btnFlash.setOnClickListener(v -> {
            isFlashOn = !isFlashOn;
            if (camera != null) camera.getCameraControl().enableTorch(isFlashOn);
        });
        btnFlipCamera.setOnClickListener(v -> {
            lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK)
                    ? CameraSelector.LENS_FACING_FRONT
                    : CameraSelector.LENS_FACING_BACK;
            startCamera();
        });
        btnConfirm.setOnClickListener(v -> confirmAndSave());
        btnRetakeFront.setOnClickListener(v -> retakeFront());
        btnRetakeBack.setOnClickListener(v -> retakeBack());

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    // ─── Camera Setup ─────────────────────────────────────────────────────────

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(requireContext(),
                    "Camera permission required.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                requireActivity().runOnUiThread(this::bindCameraUseCases);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera init failed: " + e.getMessage());
            }
        }, cameraExecutor);
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;
        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing).build();
        Preview preview = new Preview.Builder().build();
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();
        try {
            cameraProvider.unbindAll();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            camera = cameraProvider.bindToLifecycle(
                    getViewLifecycleOwner(), selector, preview, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Binding failed: " + e.getMessage());
        }
    }

    // ─── Capture → Preview → OCR ──────────────────────────────────────────────
    //
    // Flow:
    //   1. Guard taps Capture — photo taken immediately (camera is still)
    //   2. Captured image shown in thumbnail straight away
    //   3. Blur check on original image
    //   4. Yellow zone masking  (front: signature strip / back: middle section)
    //   5. Registrar zone masking on back ID (spatial — middle vertical strip)
    //   6. OCR on fully masked still image
    //   7. Review dialog with extracted fields
    //
    private void captureImage() {
        if (imageCapture == null) return;
        if (frontCaptured && backCaptured) {
            Toast.makeText(requireContext(),
                    "Both sides captured. Use Retake if needed.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnCapture.setEnabled(false);
        setUiLoading(true);
        tvInstruction.setText("Capturing...");

        imageCapture.takePicture(
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageCapturedCallback() {

                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        int rotation  = imageProxy.getImageInfo().getRotationDegrees();
                        Bitmap bitmap = imageProxyToBitmap(imageProxy);
                        imageProxy.close();

                        if (bitmap == null) {
                            setUiLoading(false);
                            btnCapture.setEnabled(true);
                            showRetakeDialog("Could not read image. Please try again.");
                            return;
                        }

                        boolean isFront = !frontCaptured;

                        // ── Show still in thumbnail immediately ───────────────────
                        if (isFront) imgFrontPreview.setImageBitmap(bitmap);
                        else         imgBackPreview.setImageBitmap(bitmap);
                        tvInstruction.setText("Scanning " +
                                (isFront ? "front" : "back") + "...");

                        // ── Blur check ────────────────────────────────────────────
                        double blurScore = getBlurScore(bitmap);
                        Log.d(TAG, "Blur score: " + blurScore);
                        if (blurScore < BLUR_THRESHOLD) {
                            resetThumbnail(isFront);
                            setUiLoading(false);
                            btnCapture.setEnabled(true);
                            showRetakeDialog("Image too blurry (score: "
                                    + String.format("%.1f", blurScore)
                                    + ").\nHold the camera steady and retake.");
                            return;
                        }

                        // ── Masking pipeline ──────────────────────────────────────
                        // Start with a mutable copy.
                        Bitmap masked = maskYellowRegions(bitmap);

                        // On the back ID, also blank out the registrar/stamp zone
                        // (middle vertical strip) so OCR never sees the disclaimer,
                        // signature, registrar name, or semester stamp.
                        if (!isFront) {
                            masked = maskRegistrarZone(masked);
                        }

                        // ── OCR on masked still ───────────────────────────────────
                        runOcr(masked, rotation, isFront, bitmap);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        setUiLoading(false);
                        btnCapture.setEnabled(true);
                        Log.e(TAG, "Capture error: " + e.getMessage());
                        Toast.makeText(requireContext(),
                                "Capture failed. Try again.", Toast.LENGTH_SHORT).show();
                        updateInstruction();
                    }
                }
        );
    }

    // ─── OCR on still image ───────────────────────────────────────────────────

    private void runOcr(Bitmap ocrBitmap, int rotation,
                        boolean isFront, Bitmap originalBitmap) {
        InputImage inputImage = InputImage.fromBitmap(ocrBitmap, rotation);
        TextRecognizer recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(inputImage)
                .addOnSuccessListener(visionText -> {
                    setUiLoading(false);
                    btnCapture.setEnabled(true);

                    String rawText = visionText.getText();
                    Log.d(TAG, "OCR Raw (" + (isFront ? "Front" : "Back") + "):\n" + rawText);

                    StudentData tempData = new StudentData();
                    copyStudentData(studentData, tempData);

                    int fieldCount = isFront
                            ? IdOcrParser.parseFront(rawText, tempData)
                            : IdOcrParser.parseBack(rawText, tempData);

                    int minFields = isFront
                            ? IdOcrParser.FRONT_MIN_FIELDS
                            : IdOcrParser.BACK_MIN_FIELDS;

                    if (fieldCount < minFields) {
                        resetThumbnail(isFront);
                        showRetakeDialog(
                                "Only " + fieldCount + " field(s) detected.\n"
                                        + "Ensure the ID is well-lit, flat, and fully in frame.\n\n"
                                        + "Detected text:\n" + rawText);
                        updateInstruction();
                        return;
                    }

                    if (isFront) showFrontReviewDialog(originalBitmap, tempData);
                    else         showBackReviewDialog(originalBitmap, tempData);
                })
                .addOnFailureListener(e -> {
                    setUiLoading(false);
                    btnCapture.setEnabled(true);
                    Log.e(TAG, "OCR failed: " + e.getMessage());
                    resetThumbnail(isFront);
                    showRetakeDialog("OCR failed. Please retake the image.");
                    updateInstruction();
                });
    }

    // ─── Masking — Yellow Regions ─────────────────────────────────────────────
    //
    // Detects bright yellow rectangles (the painted "Do Not Scan" zones on the
    // physical ID) and fills them with white. Works by finding contiguous bands
    // of rows where a significant proportion of pixels are yellow.
    //
    // Returns a NEW mutable bitmap — the original is not modified.
    //
    private Bitmap maskYellowRegions(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();

        Bitmap mutable = src.copy(Bitmap.Config.ARGB_8888, true);
        int[] pixels = new int[w * h];
        mutable.getPixels(pixels, 0, w, 0, 0, w, h);

        int rowThreshold  = w / 6;       // min yellow pixels per row
        boolean[] yellowRow = new boolean[h];

        for (int y = 0; y < h; y++) {
            int count = 0;
            for (int x = 0; x < w; x++) {
                if (isYellow(pixels[y * w + x])) count++;
            }
            yellowRow[y] = (count >= rowThreshold);
        }

        int y = 0;
        while (y < h) {
            if (!yellowRow[y]) { y++; continue; }

            int bandStart = y;
            while (y < h && yellowRow[y]) y++;
            int bandEnd = y - 1;

            if ((bandEnd - bandStart + 1) < h / 100) continue; // too thin — noise

            int xMin = w, xMax = 0;
            for (int row = bandStart; row <= bandEnd; row++) {
                for (int col = 0; col < w; col++) {
                    if (isYellow(pixels[row * w + col])) {
                        if (col < xMin) xMin = col;
                        if (col > xMax) xMax = col;
                    }
                }
            }

            int area = (xMax - xMin + 1) * (bandEnd - bandStart + 1);
            if (area < YELLOW_MIN_AREA) continue;

            Log.d(TAG, "Yellow mask: rows=" + bandStart + "-" + bandEnd
                    + " cols=" + xMin + "-" + xMax);
            for (int row = bandStart; row <= bandEnd; row++) {
                for (int col = xMin; col <= xMax; col++) {
                    pixels[row * w + col] = Color.WHITE;
                }
            }
        }

        mutable.setPixels(pixels, 0, w, 0, 0, w, h);
        return mutable;
    }

    private boolean isYellow(int pixel) {
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8)  & 0xFF;
        int b =  pixel        & 0xFF;
        return r >= YELLOW_R_MIN && g >= YELLOW_G_MIN && b <= YELLOW_B_MAX;
    }

    // ─── Masking — Registrar Zone (back ID only) ──────────────────────────────
    //
    // The registrar zone sits between the personal contact row and the emergency
    // section — roughly the middle quarter of the card's vertical height.
    // We white it out completely so ML Kit never sees the disclaimer text,
    // registrar name/title, signature, or semester stamp.
    //
    // REGISTRAR_ZONE_TOP and REGISTRAR_ZONE_BOTTOM (0.0–1.0) control the crop.
    // Tune them if testing shows the mask is cutting into real data fields.
    //
    private Bitmap maskRegistrarZone(Bitmap src) {
        int w       = src.getWidth();
        int h       = src.getHeight();
        int yTop    = (int) (h * REGISTRAR_ZONE_TOP);
        int yBottom = (int) (h * REGISTRAR_ZONE_BOTTOM);

        // src may already be mutable from maskYellowRegions — reuse it safely
        Bitmap mutable = src.isMutable()
                ? src
                : src.copy(Bitmap.Config.ARGB_8888, true);

        Canvas canvas = new Canvas(mutable);
        Paint  paint  = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, yTop, w, yBottom, paint);

        Log.d(TAG, "Registrar zone masked: rows " + yTop + "-" + yBottom
                + " of " + h);
        return mutable;
    }

    // ─── Blur Detection ───────────────────────────────────────────────────────

    private double getBlurScore(Bitmap bitmap) {
        Bitmap gray = Bitmap.createBitmap(
                bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(gray);
        Paint  paint  = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(bitmap, 0, 0, paint);

        int sampleW = Math.min(gray.getWidth(),  640);
        int sampleH = Math.min(gray.getHeight(), 480);
        int startX  = (gray.getWidth()  - sampleW) / 2;
        int startY  = (gray.getHeight() - sampleH) / 2;

        int[] pixels = new int[sampleW * sampleH];
        gray.getPixels(pixels, 0, sampleW, startX, startY, sampleW, sampleH);

        double sum = 0, sumSq = 0;
        int count = 0;

        for (int row = 1; row < sampleH - 1; row++) {
            for (int col = 1; col < sampleW - 1; col++) {
                int center = (pixels[row * sampleW + col]           >> 16) & 0xFF;
                int top    = (pixels[(row - 1) * sampleW + col]     >> 16) & 0xFF;
                int bottom = (pixels[(row + 1) * sampleW + col]     >> 16) & 0xFF;
                int left   = (pixels[row * sampleW + (col - 1)]     >> 16) & 0xFF;
                int right  = (pixels[row * sampleW + (col + 1)]     >> 16) & 0xFF;
                double lap = top + bottom + left + right - 4.0 * center;
                sum   += lap;
                sumSq += lap * lap;
                count++;
            }
        }

        if (count == 0) return 0;
        double mean = sum / count;
        return (sumSq / count) - (mean * mean);
    }

    // ─── Review Dialogs ───────────────────────────────────────────────────────

    private void showFrontReviewDialog(Bitmap bitmap, StudentData tempData) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Review Front ID Data");
        builder.setCancelable(false);

        ScrollView  scrollView = new ScrollView(requireContext());
        LinearLayout layout    = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText etStudentId = makeField(layout, "Student ID Number", tempData.studentId);
        EditText etName      = makeField(layout, "Student Name",      tempData.studentName);
        EditText etCourse    = makeField(layout, "Course",            tempData.course);
        EditText etCollege   = makeField(layout, "College",           tempData.college);

        if (tempData.studentId   == null || tempData.studentName == null
                || tempData.course == null || tempData.college   == null) {
            addWarning(layout,
                    "⚠️ Some fields were not detected. Please fill them in manually.");
        }

        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("Confirm Front ✓", (dialog, which) -> {
            studentData.studentId   = etStudentId.getText().toString().trim();
            studentData.studentName = etName.getText().toString().trim();
            studentData.course      = etCourse.getText().toString().trim();
            studentData.college     = etCollege.getText().toString().trim();
            frontCaptured = true;
            btnRetakeFront.setVisibility(View.VISIBLE);
            tvInstruction.setText("Front confirmed! Now align BACK of ID.");
            Toast.makeText(requireContext(),
                    "Front confirmed! Now scan the back.", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("↺ Retake Front", (dialog, which) -> {
            imgFrontPreview.setImageResource(android.R.color.darker_gray);
            dialog.dismiss();
            updateInstruction();
        });

        builder.show();
    }

    private void showBackReviewDialog(Bitmap bitmap, StudentData tempData) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Review Back ID Data");
        builder.setCancelable(false);

        ScrollView  scrollView = new ScrollView(requireContext());
        LinearLayout layout    = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText etAddress   = makeField(layout, "Address",                  tempData.address);
        EditText etContact   = makeField(layout, "Contact Number",           tempData.contactNumber);
        EditText etEmerName  = makeField(layout, "Emergency Contact Name",   tempData.emergencyContact);
        EditText etEmerAddr  = makeField(layout, "Emergency Address",        tempData.emergencyAddress);
        EditText etEmerPhone = makeField(layout, "Emergency Contact Number", tempData.emergencyContactNumber);

        if (tempData.address == null || tempData.contactNumber == null) {
            addWarning(layout,
                    "⚠️ Some fields were not detected. Please fill them in manually.");
        }

        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("Confirm Back ✓", (dialog, which) -> {
            studentData.address                = etAddress.getText().toString().trim();
            studentData.contactNumber          = etContact.getText().toString().trim();
            studentData.emergencyContact       = etEmerName.getText().toString().trim();
            studentData.emergencyAddress       = etEmerAddr.getText().toString().trim();
            studentData.emergencyContactNumber = etEmerPhone.getText().toString().trim();
            backCaptured = true;
            btnRetakeBack.setVisibility(View.VISIBLE);
            btnConfirm.setEnabled(true);
            tvInstruction.setText("Both sides confirmed! Ready to save.");
            Toast.makeText(requireContext(),
                    "Back confirmed! Ready to save.", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("↺ Retake Back", (dialog, which) -> {
            imgBackPreview.setImageResource(android.R.color.darker_gray);
            dialog.dismiss();
            tvInstruction.setText("Align BACK of ID within frame");
        });

        builder.show();
    }

    // ─── Retake Dialog ────────────────────────────────────────────────────────

    private void showRetakeDialog(String reason) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Retake Required")
                .setMessage(reason)
                .setCancelable(false)
                .setPositiveButton("Retake Now", (d, w) -> {
                    d.dismiss();
                    updateInstruction();
                })
                .show();
    }

    // ─── Retake Actions ───────────────────────────────────────────────────────

    private void retakeFront() {
        frontCaptured           = false;
        studentData.studentId   = null;
        studentData.studentName = null;
        studentData.course      = null;
        studentData.college     = null;
        imgFrontPreview.setImageResource(android.R.color.darker_gray);
        btnRetakeFront.setVisibility(View.GONE);
        btnConfirm.setEnabled(false);
        tvInstruction.setText("Align FRONT of ID within frame");
        Toast.makeText(requireContext(),
                "Ready to retake front ID.", Toast.LENGTH_SHORT).show();
    }

    private void retakeBack() {
        backCaptured                       = false;
        studentData.address                = null;
        studentData.contactNumber          = null;
        studentData.emergencyContact       = null;
        studentData.emergencyAddress       = null;
        studentData.emergencyContactNumber = null;
        imgBackPreview.setImageResource(android.R.color.darker_gray);
        btnRetakeBack.setVisibility(View.GONE);
        btnConfirm.setEnabled(false);
        tvInstruction.setText("Align BACK of ID within frame");
        Toast.makeText(requireContext(),
                "Ready to retake back ID.", Toast.LENGTH_SHORT).show();
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    private void confirmAndSave() {
        btnConfirm.setEnabled(false);
        btnConfirm.setText("Saving...");
        ApiService api = RetrofitClient.getInstance().create(ApiService.class);
        api.saveStudent(studentData).enqueue(new retrofit2.Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<ApiResponse> call,
                                   @NonNull retrofit2.Response<ApiResponse> response) {
                btnConfirm.setText("Confirm\n& Save");
                if (response.isSuccessful() && response.body() != null
                        && response.body().success) {
                    Toast.makeText(requireContext(),
                            "✅ Student saved successfully!", Toast.LENGTH_LONG).show();
                    resetScanner();
                } else {
                    btnConfirm.setEnabled(true);
                    Toast.makeText(requireContext(),
                            "Server error. Check PHP/DB.", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull retrofit2.Call<ApiResponse> call,
                                  @NonNull Throwable t) {
                btnConfirm.setEnabled(true);
                btnConfirm.setText("Confirm\n& Save");
                Toast.makeText(requireContext(),
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ─── UI Helpers ───────────────────────────────────────────────────────────

    private EditText makeField(LinearLayout parent, String label, String value) {
        Context ctx = requireContext();

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(12f);
        tv.setTextColor(0xFF414754);
        tv.setPadding(0, 12, 0, 2);
        parent.addView(tv);

        EditText et = new EditText(ctx);
        et.setText(value != null ? value : "");
        et.setHint("Enter " + label);
        et.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        et.setPadding(8, 8, 8, 8);
        parent.addView(et);
        return et;
    }

    private void addWarning(LinearLayout layout, String message) {
        TextView warning = new TextView(requireContext());
        warning.setText(message);
        warning.setTextColor(0xFFE65100);
        warning.setTextSize(12f);
        warning.setPadding(0, 8, 0, 8);
        layout.addView(warning, 0);
    }

    private void copyStudentData(StudentData from, StudentData to) {
        to.studentId              = from.studentId;
        to.studentName            = from.studentName;
        to.course                 = from.course;
        to.college                = from.college;
        to.address                = from.address;
        to.contactNumber          = from.contactNumber;
        to.emergencyContact       = from.emergencyContact;
        to.emergencyAddress       = from.emergencyAddress;
        to.emergencyContactNumber = from.emergencyContactNumber;
    }

    private void setUiLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void resetThumbnail(boolean isFront) {
        if (isFront) imgFrontPreview.setImageResource(android.R.color.darker_gray);
        else         imgBackPreview.setImageResource(android.R.color.darker_gray);
    }

    private void updateInstruction() {
        if (!frontCaptured)     tvInstruction.setText("Align FRONT of ID within frame");
        else if (!backCaptured) tvInstruction.setText("Align BACK of ID within frame");
        else                    tvInstruction.setText("Both sides confirmed!");
    }

    private void resetScanner() {
        frontCaptured = false;
        backCaptured  = false;
        studentData   = new StudentData();
        imgFrontPreview.setImageResource(android.R.color.darker_gray);
        imgBackPreview.setImageResource(android.R.color.darker_gray);
        btnConfirm.setEnabled(false);
        btnRetakeFront.setVisibility(View.GONE);
        btnRetakeBack.setVisibility(View.GONE);
        tvInstruction.setText("Align FRONT of ID within frame");
    }

    // ─── ImageProxy → Bitmap ─────────────────────────────────────────────────

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();

            if (planes.length == 1) {
                ByteBuffer buffer = planes[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21   = new byte[ySize + uSize + vSize];
            byte[] vBytes = new byte[vSize];
            byte[] uBytes = new byte[uSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(vBytes);
            uBuffer.get(uBytes);

            for (int i = 0; i < vSize; i++) {
                nv21[ySize + i * 2]     = vBytes[i];
                if (i < uSize)
                    nv21[ySize + i * 2 + 1] = uBytes[i];
            }

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    imageProxy.getWidth(), imageProxy.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()),
                    95, out);
            byte[] bytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        } catch (Exception e) {
            Log.e(TAG, "imageProxyToBitmap failed: " + e.getMessage());
            return null;
        }
    }
}