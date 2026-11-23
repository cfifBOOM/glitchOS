package app.contouros.home;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

final class TileBoard extends ViewGroup {
    private final int gap;
    private int columns=4;
    private TilePacking.Cell[] cells=new TilePacking.Cell[0];
    private float unit;
    TileBoard(Context c,int columns){super(c);this.columns=columns==6?6:4;gap=Math.round(6*getResources().getDisplayMetrics().density);}
    void addTile(View view,TileSpec spec){view.setTag(spec);addView(view,new LayoutParams(0,0));}
    @Override protected void onMeasure(int ws,int hs) {
        int width=MeasureSpec.getSize(ws),count=getChildCount();
        int cols=columns;
        if(width/getResources().getDisplayMetrics().density<300||getResources().getConfiguration().fontScale>=1.45f)cols=4;
        unit=(width-gap*(cols-1))/(float)cols;
        int[] widths=new int[count],heights=new int[count];
        for(int i=0;i<count;i++){TileSpec t=(TileSpec)getChildAt(i).getTag();widths[i]=t.columns();heights[i]=t.rows();}
        cells=TilePacking.pack(widths,heights,cols);int bottom=0;
        for(int i=0;i<count;i++){
            TilePacking.Cell c=cells[i];
            int left=Math.round(c.x*(unit+gap)),top=Math.round(c.y*(unit+gap));
            int right=Math.round((c.x+c.width)*(unit+gap))-gap;
            int end=Math.round((c.y+c.height)*(unit+gap))-gap;
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(right-left,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(end-top,MeasureSpec.EXACTLY));
            bottom=Math.max(bottom,end);
        }
        setMeasuredDimension(resolveSize(width,ws),resolveSize(bottom,hs));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
        for(int i=0;i<getChildCount();i++){
            View v=getChildAt(i);TilePacking.Cell c=cells[i];
            int x=Math.round(c.x*(unit+gap)),y=Math.round(c.y*(unit+gap));
            if(rtl)x=getWidth()-x-v.getMeasuredWidth();
            v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());
        }
    }
}
