package com.example.minenote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class TokenRingView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int percent = -1;

    public TokenRingView(Context context) {
        super(context);
        init();
    }

    public TokenRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TokenRingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setPercent(int percent) {
        this.percent = Math.max(0, Math.min(100, percent));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float size = Math.min(getWidth(), getHeight());
        float center = size / 2f;
        float stroke = dp(3);
        float radius = center - stroke - dp(1);

        rect.set(
                center - radius,
                center - radius,
                center + radius,
                center + radius
        );

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(0xFFE8E8E8);
        canvas.drawArc(rect, 0, 360, false, paint);

        int progressColor;
        if (percent < 0) {
            progressColor = 0xFFD4B84C;
        } else if (percent <= 20) {
            progressColor = 0xFFE57373;
        } else if (percent <= 50) {
            progressColor = 0xFFF0B429;
        } else {
            progressColor = 0xFFD4B84C;
        }

        paint.setColor(progressColor);
        canvas.drawArc(
                rect,
                -90,
                percent < 0 ? 360 : percent * 3.6f,
                false,
                paint
        );

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF444444);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(dp(11));

        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = center
                - (metrics.ascent + metrics.descent) / 2f;

        canvas.drawText(
                percent < 0 ? "AI" : String.valueOf(percent),
                center,
                baseline,
                paint
        );
    }

    private float dp(float value) {
        return value * getResources()
                .getDisplayMetrics().density;
    }
}
