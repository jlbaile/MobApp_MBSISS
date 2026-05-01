package com.example.mbsiss;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * ScanOverlayView — Portrait bracket edition
 *
 * The bracket guide is now VERTICAL (portrait / tall) so the guard holds
 * the phone upright AND the ID card upright. This matches natural handling
 * and keeps OCR text running left-to-right across the card width.
 *
 * Physical CNSC ID dimensions: 85.6 mm wide × 54 mm tall (landscape card).
 * When the card is held UPRIGHT the scanning face is 54 mm wide × 85.6 mm tall.
 * Aspect ratio (width ÷ height) = 54 ÷ 85.6 ≈ 0.631
 *
 * Back mode adds 12% extra HEIGHT so the emergency contact section at the
 * bottom of the card is fully inside the bracket.
 *
 * Public API
 * ──────────
 *   setBackMode(boolean)   call after front is confirmed
 *   getScanRect()          returns bracket rect in View coordinates
 *                          → CameraFragment uses this to crop the bitmap
 */
public class Scanoverlayview extends View {

    // Portrait ID aspect ratio: width ÷ height = 54 ÷ 85.6 ≈ 0.631
    private static final float ID_ASPECT_RATIO  = 54.0f / 85.6f;

    // Back-ID guide is 12% taller to capture the emergency contact section
    private static final float BACK_HEIGHT_MULT = 1.12f;

    // Bracket box width = (view width) × this fraction
    // 0.78 leaves ~11% margin on each side — visible but not cramped
    private static final float WIDTH_FRAC       = 0.78f;

    // Corner bracket arm length (pixels)
    private static final float BRACKET_LEN      = 48f;
    private static final float BRACKET_WIDTH    = 5.5f;
    private static final int   BRACKET_COLOR    = 0xFFFFFFFF;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean isBackMode = false;
    private final RectF scanRect = new RectF();

    // ── Paint ─────────────────────────────────────────────────────────────────
    private final Paint bracketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ─── Constructors ─────────────────────────────────────────────────────────

    public Scanoverlayview(Context context) {
        super(context); init();
    }
    public Scanoverlayview(Context context, AttributeSet attrs) {
        super(context, attrs); init();
    }
    public Scanoverlayview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle); init();
    }

    private void init() {
        bracketPaint.setColor(BRACKET_COLOR);
        bracketPaint.setStyle(Paint.Style.STROKE);
        bracketPaint.setStrokeWidth(BRACKET_WIDTH);
        bracketPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Switch to the taller back-ID guide. Call after front is confirmed. */
    public void setBackMode(boolean backMode) {
        if (isBackMode != backMode) {
            isBackMode = backMode;
            invalidate();
        }
    }

    public boolean isBackMode() {
        return isBackMode;
    }

    /**
     * Returns the guide bracket rect in this View's coordinate space.
     * CameraFragment scales this to bitmap coordinates to crop before OCR.
     */
    public RectF getScanRect() {
        return new RectF(scanRect);
    }

    // ─── Drawing ──────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int vw = getWidth();
        int vh = getHeight();

        // ── Compute portrait bracket rect ─────────────────────────────────────
        float cardW = vw * WIDTH_FRAC;
        float cardH = cardW / ID_ASPECT_RATIO;   // tall: h > w
        if (isBackMode) cardH *= BACK_HEIGHT_MULT;

        // Clamp height so it never overflows the screen
        if (cardH > vh * 0.88f) {
            cardH = vh * 0.88f;
            cardW = cardH * ID_ASPECT_RATIO;
        }

        // Centre the rect, nudged very slightly above centre for thumb comfort
        float left = (vw - cardW) / 2f;
        float top  = (vh - cardH) / 2f - vh * 0.02f;
        scanRect.set(left, top, left + cardW, top + cardH);

        drawCornerBrackets(canvas);
    }

    private void drawCornerBrackets(Canvas canvas) {
        float l  = scanRect.left;
        float t  = scanRect.top;
        float r  = scanRect.right;
        float b  = scanRect.bottom;
        float bl = BRACKET_LEN;

        // Top-left
        canvas.drawLine(l, t + bl, l, t, bracketPaint);
        canvas.drawLine(l, t, l + bl, t, bracketPaint);
        // Top-right
        canvas.drawLine(r - bl, t, r, t, bracketPaint);
        canvas.drawLine(r, t, r, t + bl, bracketPaint);
        // Bottom-left
        canvas.drawLine(l, b - bl, l, b, bracketPaint);
        canvas.drawLine(l, b, l + bl, b, bracketPaint);
        // Bottom-right
        canvas.drawLine(r - bl, b, r, b, bracketPaint);
        canvas.drawLine(r, b, r, b - bl, bracketPaint);
    }
}