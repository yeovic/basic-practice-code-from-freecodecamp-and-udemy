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
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    public RegionSelectionView(Context context){
        super(context);
        stroke.setColor(Color.CYAN);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f*getResources().getDisplayMetrics().density);
    }
    public void setSelectionRegion(FacePatchEngine.Region region){
        selection=region;
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(selection==null||getDrawable()==null)return;
        RectF rectangle=new RectF(selection.left,selection.top,
            selection.left+selection.size,selection.top+selection.size);
        getImageMatrix().mapRect(rectangle);
        rectangle.offset(getPaddingLeft(),getPaddingTop());
        canvas.drawRect(rectangle,stroke);
    }
}