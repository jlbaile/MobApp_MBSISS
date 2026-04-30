package com.example.mbsiss;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
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

/**
 * CameraFragment — Revised approach
 *
 * OLD approach problems:
 *   - Yellow masking + registrar zone masking added heavy processing delay
 *   - Parser tried to block/skip OCR text, causing back-ID fields to be missed
 *   - Blur check added another pass over the bitmap before OCR even started
 *
 * NEW approach:
 *   1. Guard aligns ID inside corner brackets → taps Capture
 *   2. Bitmap captured and rotated immediately (no extra processing)
 *   3. Bitmap cropped to the bracket guide rect
 *   4. Cropped image shown in thumbnail
 *   5. OCR runs on the full cropped image — NO masking, NO filtering
 *   6. IdOcrParser does a best-effort parse of ALL the raw text
 *   7. Review dialog shows every extracted field pre-filled
 *      → Guard corrects anything wrong, then confirms
 *   8. On confirm → data saved to DB
 *
 * This means:
 *   - No blur check (removed — added delay and caused retakes on valid images)
 *   - No yellow masking (removed — guard just ignores those fields in dialog)
 *   - No registrar zone masking (removed — parser filters by label, guard corrects)
 *   - Processing pipeline is now just: crop → OCR → show dialog
 *   - Much faster capture-to-dialog time
 */
public class CameraFragment extends Fragment {

    private static final String TAG = "CameraFragment";
    private static final int    PERMISSION_REQUEST_CODE = 100;

    // ── UI ────────────────────────────────────────────────────────────────────
    private PreviewView     previewView;
    private Scanoverlayview scanOverlay;
    private ImageView       imgFrontPreview, imgBackPreview;
    private Button          btnCapture, btnConfirm;
    private Button          btnRetakeFront, btnRetakeBack;
    private ImageButton     btnFlash, btnFlipCamera;
    private TextView        tvInstruction;
    private ProgressBar     progressBar;

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
        scanOverlay     = view.findViewById(R.id.scanOverlay);
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

        // MINIMIZE_LATENCY = prioritise speed over image quality
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

    // ─── Capture ──────────────────────────────────────────────────────────────
    //
    // Flow:
    //   takePicture → rotate bitmap → crop to bracket rect → show thumbnail
    //   → OCR on full crop (no masking) → review dialog
    //
    // The callback executor is cameraExecutor so bitmap conversion happens
    // off the main thread. Only UI updates touch the main thread.
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

        boolean isFront = !frontCaptured;

        // Use cameraExecutor so bitmap work is off the main thread
        imageCapture.takePicture(cameraExecutor,
                new ImageCapture.OnImageCapturedCallback() {

                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        // ── Convert & rotate (off main thread) ───────────────
                        int    rotation = imageProxy.getImageInfo().getRotationDegrees();
                        Bitmap bitmap   = imageProxyToBitmap(imageProxy);
                        imageProxy.close();

                        if (bitmap == null) {
                            requireActivity().runOnUiThread(() -> {
                                setUiLoading(false);
                                btnCapture.setEnabled(true);
                                Toast.makeText(requireContext(),
                                        "Could not read image. Try again.",
                                        Toast.LENGTH_SHORT).show();
                                updateInstruction();
                            });
                            return;
                        }

                        Bitmap rotated = rotateBitmap(bitmap, rotation);
                        Bitmap cropped = cropToOverlay(rotated);

                        // ── Update UI (main thread) ───────────────────────────
                        requireActivity().runOnUiThread(() -> {
                            if (isFront) imgFrontPreview.setImageBitmap(cropped);
                            else         imgBackPreview.setImageBitmap(cropped);
                            tvInstruction.setText(
                                    "Reading " + (isFront ? "front" : "back") + "...");
                        });

                        // ── OCR immediately on the cropped image ─────────────
                        // No blur check, no masking — scan everything as-is.
                        runOcr(cropped, isFront);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        requireActivity().runOnUiThread(() -> {
                            setUiLoading(false);
                            btnCapture.setEnabled(true);
                            Log.e(TAG, "Capture error: " + e.getMessage());
                            Toast.makeText(requireContext(),
                                    "Capture failed. Try again.",
                                    Toast.LENGTH_SHORT).show();
                            updateInstruction();
                        });
                    }
                }
        );
    }

    // ─── OCR ─────────────────────────────────────────────────────────────────
    //
    // Runs ML Kit on the cropped bitmap with zero pre-filtering.
    // Whatever text the ID contains, we capture all of it.
    // IdOcrParser then does a best-effort parse, and the review dialog
    // lets the guard fix anything that was missed or wrong.
    //
    private void runOcr(Bitmap cropped, boolean isFront) {
        // InputImage.fromBitmap is fine off-main-thread
        InputImage inputImage = InputImage.fromBitmap(cropped, 0);
        TextRecognizer recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(inputImage)
                .addOnSuccessListener(visionText -> {
                    String rawText = visionText.getText();
                    Log.d(TAG, "=== OCR RAW " + (isFront ? "FRONT" : "BACK")
                            + " ===\n" + rawText + "\n===");

                    StudentData tempData = new StudentData();
                    copyStudentData(studentData, tempData);

                    if (isFront) IdOcrParser.parseFront(rawText, tempData);
                    else         IdOcrParser.parseBack(rawText, tempData);

                    // Show review dialog regardless of how many fields were found.
                    // Guard fills in anything missing manually.
                    requireActivity().runOnUiThread(() -> {
                        setUiLoading(false);
                        btnCapture.setEnabled(true);
                        if (isFront) showFrontReviewDialog(tempData, rawText);
                        else         showBackReviewDialog(tempData, rawText);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR failed: " + e.getMessage());
                    requireActivity().runOnUiThread(() -> {
                        setUiLoading(false);
                        btnCapture.setEnabled(true);
                        // Even if OCR totally fails, open the dialog blank
                        // so the guard can type the data manually
                        if (isFront) showFrontReviewDialog(new StudentData(), "");
                        else         showBackReviewDialog(new StudentData(), "");
                    });
                });
    }

    // ─── Crop to Overlay Guide Rect ───────────────────────────────────────────

    private Bitmap cropToOverlay(Bitmap bitmap) {
        RectF scanRectView = scanOverlay.getScanRect();

        int viewW = scanOverlay.getWidth();
        int viewH = scanOverlay.getHeight();
        int bmpW  = bitmap.getWidth();
        int bmpH  = bitmap.getHeight();

        if (viewW == 0 || viewH == 0) {
            Log.w(TAG, "Overlay not laid out yet — skipping crop");
            return bitmap;
        }

        float scaleX = (float) bmpW / viewW;
        float scaleY = (float) bmpH / viewH;

        int cropLeft   = Math.max(0,    (int) (scanRectView.left   * scaleX));
        int cropTop    = Math.max(0,    (int) (scanRectView.top    * scaleY));
        int cropRight  = Math.min(bmpW, (int) (scanRectView.right  * scaleX));
        int cropBottom = Math.min(bmpH, (int) (scanRectView.bottom * scaleY));

        int cropW = cropRight  - cropLeft;
        int cropH = cropBottom - cropTop;

        if (cropW <= 0 || cropH <= 0) {
            Log.w(TAG, "Crop rect invalid — using full bitmap");
            return bitmap;
        }

        Log.d(TAG, "Crop: (" + cropLeft + "," + cropTop + ")→("
                + cropRight + "," + cropBottom + ")  bitmap=" + bmpW + "×" + bmpH);
        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH);
    }

    // ─── Bitmap Rotation ──────────────────────────────────────────────────────

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    // ─── Review Dialog — Front ID ─────────────────────────────────────────────
    //
    // Shows pre-filled fields from the parser. Guard can edit any field.
    // A collapsible "Raw OCR text" section lets the guard copy-paste if needed.
    // Confirming accepts whatever is currently in the EditTexts.
    //
    private void showFrontReviewDialog(StudentData tempData, String rawText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("✅ Front ID Scanned — Review & Confirm");
        builder.setCancelable(false);

        ScrollView   scroll = new ScrollView(requireContext());
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 24);

        // Instruction note
        addNote(layout,
                "Fields below were auto-filled from the scan. "
                        + "Fix anything that looks wrong, then tap Confirm.");

        EditText etStudentId = makeField(layout, "Student ID Number", tempData.studentId);
        EditText etName      = makeField(layout, "Student Name",      tempData.studentName);
        EditText etCourse    = makeField(layout, "Course",            tempData.course);
        EditText etCollege   = makeField(layout, "College",           tempData.college);

        // Raw OCR dump — helps guard find missed text
        if (!rawText.isEmpty()) {
            addRawOcrSection(layout, rawText);
        }

        scroll.addView(layout);
        builder.setView(scroll);

        builder.setPositiveButton("Confirm Front ✓", (dialog, which) -> {
            studentData.studentId   = etStudentId.getText().toString().trim();
            studentData.studentName = etName.getText().toString().trim();
            studentData.course      = etCourse.getText().toString().trim();
            studentData.college     = etCollege.getText().toString().trim();
            frontCaptured = true;
            scanOverlay.setBackMode(true);
            btnRetakeFront.setVisibility(View.VISIBLE);
            tvInstruction.setText("Front confirmed! Now align BACK of ID.");
            Toast.makeText(requireContext(),
                    "Front confirmed! Now scan the back.", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("↺ Retake Front", (dialog, which) -> {
            imgFrontPreview.setImageResource(android.R.color.darker_gray);
            updateInstruction();
        });

        builder.show();
    }

    // ─── Review Dialog — Back ID ──────────────────────────────────────────────

    private void showBackReviewDialog(StudentData tempData, String rawText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("✅ Back ID Scanned — Review & Confirm");
        builder.setCancelable(false);

        ScrollView   scroll = new ScrollView(requireContext());
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 24);

        addNote(layout,
                "Fields below were auto-filled from the scan. "
                        + "Fix anything that looks wrong, then tap Confirm.");

        EditText etAddress   = makeField(layout, "Address",                  tempData.address);
        EditText etContact   = makeField(layout, "Contact Number",           tempData.contactNumber);
        EditText etEmerName  = makeField(layout, "Emergency Contact Name",   tempData.emergencyContact);
        EditText etEmerAddr  = makeField(layout, "Emergency Address",        tempData.emergencyAddress);
        EditText etEmerPhone = makeField(layout, "Emergency Contact Number", tempData.emergencyContactNumber);

        if (!rawText.isEmpty()) {
            addRawOcrSection(layout, rawText);
        }

        scroll.addView(layout);
        builder.setView(scroll);

        builder.setPositiveButton("Confirm Back ✓", (dialog, which) -> {
            studentData.address                = etAddress.getText().toString().trim();
            studentData.contactNumber          = etContact.getText().toString().trim();
            studentData.emergencyContact       = etEmerName.getText().toString().trim();
            studentData.emergencyAddress       = etEmerAddr.getText().toString().trim();
            studentData.emergencyContactNumber = etEmerPhone.getText().toString().trim();
            backCaptured = true;
            btnRetakeBack.setVisibility(View.VISIBLE);
            btnConfirm.setEnabled(true);
            btnConfirm.setVisibility(View.VISIBLE);
            tvInstruction.setText("Both sides confirmed! Tap Save.");
            Toast.makeText(requireContext(),
                    "Back confirmed! Ready to save.", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("↺ Retake Back", (dialog, which) -> {
            imgBackPreview.setImageResource(android.R.color.darker_gray);
            tvInstruction.setText("Align BACK of ID within frame");
        });

        builder.show();
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
        btnConfirm.setVisibility(View.GONE);
        scanOverlay.setBackMode(false);
        tvInstruction.setText("Align FRONT of ID within frame");
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
        btnConfirm.setVisibility(View.GONE);
        tvInstruction.setText("Align BACK of ID within frame");
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
                btnConfirm.setText("Confirm & Save");
                if (response.isSuccessful() && response.body() != null
                        && response.body().success) {
                    Toast.makeText(requireContext(),
                            "✅ Student saved!", Toast.LENGTH_LONG).show();
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
                btnConfirm.setText("Confirm & Save");
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
        tv.setTextColor(0xFF555555);
        tv.setPadding(0, 16, 0, 2);
        parent.addView(tv);

        EditText et = new EditText(ctx);
        et.setText(value != null ? value : "");
        et.setHint(value == null ? "⚠ Not detected — enter manually" : "");
        et.setHintTextColor(0xFFE65100);
        et.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        et.setPadding(8, 8, 8, 8);
        parent.addView(et);
        return et;
    }

    private void addNote(LinearLayout layout, String message) {
        TextView tv = new TextView(requireContext());
        tv.setText(message);
        tv.setTextColor(0xFF1565C0);
        tv.setTextSize(12f);
        tv.setPadding(0, 8, 0, 16);
        layout.addView(tv);
    }

    /**
     * Adds a collapsible section at the bottom of the dialog showing the
     * full raw OCR text. Useful when a field wasn't auto-detected —
     * the guard can read the raw dump and type the value manually.
     */
    private void addRawOcrSection(LinearLayout layout, String rawText) {
        TextView header = new TextView(requireContext());
        header.setText("▼ Raw scan text (tap to expand)");
        header.setTextColor(0xFF888888);
        header.setTextSize(11f);
        header.setPadding(0, 24, 0, 4);
        layout.addView(header);

        TextView body = new TextView(requireContext());
        body.setText(rawText);
        body.setTextColor(0xFF444444);
        body.setTextSize(10f);
        body.setPadding(8, 4, 8, 8);
        body.setBackgroundColor(0xFFF5F5F5);
        body.setVisibility(View.GONE);   // collapsed by default
        layout.addView(body);

        header.setOnClickListener(v -> {
            if (body.getVisibility() == View.GONE) {
                body.setVisibility(View.VISIBLE);
                header.setText("▲ Raw scan text (tap to collapse)");
            } else {
                body.setVisibility(View.GONE);
                header.setText("▼ Raw scan text (tap to expand)");
            }
        });
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

    private void updateInstruction() {
        if (!frontCaptured)      tvInstruction.setText("Align FRONT of ID within frame");
        else if (!backCaptured)  tvInstruction.setText("Align BACK of ID within frame");
        else                     tvInstruction.setText("Both sides confirmed!");
    }

    private void resetScanner() {
        frontCaptured = false;
        backCaptured  = false;
        studentData   = new StudentData();
        imgFrontPreview.setImageResource(android.R.color.darker_gray);
        imgBackPreview.setImageResource(android.R.color.darker_gray);
        btnConfirm.setEnabled(false);
        btnConfirm.setVisibility(View.GONE);
        btnConfirm.setText("Confirm & Save");
        btnRetakeFront.setVisibility(View.GONE);
        btnRetakeBack.setVisibility(View.GONE);
        scanOverlay.setBackMode(false);
        tvInstruction.setText("Align FRONT of ID within frame");
    }

    // ─── ImageProxy → Bitmap ─────────────────────────────────────────────────

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();

            // JPEG format (some devices)
            if (planes.length == 1) {
                ByteBuffer buffer = planes[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }

            // YUV_420_888 format (most devices)
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
                    90, out);
            byte[] bytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        } catch (Exception e) {
            Log.e(TAG, "imageProxyToBitmap failed: " + e.getMessage());
            return null;
        }
    }
}