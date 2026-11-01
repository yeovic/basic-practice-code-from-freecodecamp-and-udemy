package org.velvetatelier;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Color;
import android.widget.ImageView;

/**
 * Draws an image-space region on top of the displayed Bitmap. No full-image
 * selection overlay Bitmap is allocated or copied when the user moves a crop.
 */
public final class RegionSelectionView extends ImageView {
    private FacePatchEngine.Region selection;
    private boolean maskPreview=false;
    private float coverage=.82f,feather=.13f;
    private final Paint tint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint featherStroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    public RegionSelectionView(Context context){
        super(context);
        tint.setColor(0x3500ffff);
        tint.setStyle(Paint.Style.FILL);
        featherStroke.setColor(0xaa00ffff);
        featherStroke.setStyle(Paint.Style.STROKE);
        featherStroke.setStrokeWidth(1.5f*getResources().getDisplayMetrics().density);
        stroke.setColor(Color.CYAN);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f*getResources().getDisplayMetrics().density);
    }
    public void setSelectionRegion(FacePatchEngine.Region region){
        selection=region;
        invalidate();
    }
    /** Visual approximation of the editable native-mask footprint. */
    public void setMaskPreview(boolean enabled,int coveragePercent,int featherPercent){
        maskPreview=enabled;
        coverage=Math.max(.40f,Math.min(.95f,coveragePercent/100f));
        feather=Math.max(.03f,Math.min(.30f,featherPercent/100f));
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(selection==null||getDrawable()==null)return;
        RectF rectangle=new RectF(selection.left,selection.top,
            selection.left+selection.size,selection.top+selection.size);
        getImageMatrix().mapRect(rectangle);
        rectangle.offset(getPaddingLeft(),getPaddingTop());
        if(maskPreview){
            float cx=rectangle.centerX(),cy=rectangle.centerY();
            float outer=rectangle.width()*0.5f*coverage;
            float inner=rectangle.width()*0.5f*Math.max(0f,coverage-feather);
            RectF editable=new RectF(cx-inner,cy-inner,cx+inner,cy+inner);
            RectF softBorder=new RectF(cx-outer,cy-outer,cx+outer,cy+outer);
            canvas.drawOval(editable,tint);
            canvas.drawOval(softBorder,featherStroke);
        }
        canvas.drawRect(rectangle,stroke);
    }
}