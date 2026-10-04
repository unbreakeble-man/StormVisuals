package dev.stormvisuals.module;

import java.util.ArrayList;
import java.util.List;

/** Persisted client settings; all dimensions are GUI units. */
public final class CrosshairSettings {
    public enum Style {
        SQUARE("Квадрат"), RING("Кольцо"), CROSS("Крест как в CS"), V("V"), CUSTOM("Кастом");
        public final String label;
        Style(String label) { this.label = label; }
    }
    public enum Kind {
        LINE("Линия"), RING("Кольцо"), SQUARE("Квадрат"), DOT("Точка"), V("V");
        public final String label;
        Kind(String label) { this.label = label; }
    }
    public static final class Element {
        public Kind kind = Kind.LINE;
        public float x, y, rotation;
        public float size = 5, thickness = 1, opacity = 100;
        public boolean visible = true, filled, ownSettings;
        public int color = 0xFFFFFF, outlineColor;
        public boolean outline = true;
        public float outlineWidth = 1;
        public Element() {}
        public Element(Kind kind) { this.kind = kind; size = kind == Kind.DOT ? 2 : kind == Kind.LINE ? 5 : 12; }
        public Element copy() {
            Element e = new Element(kind);
            e.x=x; e.y=y; e.rotation=rotation; e.size=size; e.thickness=thickness; e.opacity=opacity;
            e.visible=visible; e.filled=filled; e.ownSettings=ownSettings; e.color=color;
            e.outlineColor=outlineColor; e.outline=outline; e.outlineWidth=outlineWidth;
            return e;
        }
        public void validate() {
            if (kind == null) kind = Kind.LINE;
            x=limit(x,-64,64,0); y=limit(y,-64,64,0); rotation=limit(rotation,-180,180,0);
            size=limit(size,.5f,64,5); thickness=limit(thickness,.25f,8,1);
            opacity=limit(opacity,0,100,100); outlineWidth=limit(outlineWidth,.25f,4,1);
            color &= 0xFFFFFF; outlineColor &= 0xFFFFFF;
        }
    }
    public int version = 1;
    public boolean enabled, hover, dynamic;
    public boolean weaponsOnly = true, outline = true;
    public Style style = Style.SQUARE;
    public int color = 0xFFFFFF, hoverColor = 0x55FF55, outlineColor;
    public float opacity = 100, outlineWidth = 1;
    public float squareSize = 10, ringSize = 12, crossLength = 5, crossWidth = 1, crossGap = 3, vSize = 10;
    public List<Element> elements = defaultElements();

    private static List<Element> defaultElements() {
        List<Element> list = new ArrayList<>();
        for (int i=0;i<4;i++) {
            Element e = new Element(Kind.LINE);
            e.rotation=i*90; if(e.rotation>180)e.rotation-=360;
            double a=Math.toRadians(e.rotation); e.x=(float)Math.cos(a)*5.5f; e.y=(float)Math.sin(a)*5.5f;
            list.add(e);
        }
        return list;
    }
    public void validate() {
        version=1; if(style==null)style=Style.SQUARE;
        color&=0xFFFFFF; hoverColor&=0xFFFFFF; outlineColor&=0xFFFFFF;
        opacity=limit(opacity,0,100,100); outlineWidth=limit(outlineWidth,.25f,4,1);
        squareSize=limit(squareSize,2,64,10); ringSize=limit(ringSize,2,64,12);
        crossLength=limit(crossLength,.5f,64,5); crossWidth=limit(crossWidth,.25f,8,1);
        crossGap=limit(crossGap,0,32,3); vSize=limit(vSize,2,64,10);
        if(elements==null)elements=defaultElements();
        List<Element> valid=new ArrayList<>();
        for(Element e:elements)if(e!=null && valid.size()<32){e.validate();valid.add(e);}
        elements=valid;
    }
    public static float limit(float value,float min,float max,float fallback) {
        return Float.isFinite(value)?Math.clamp(value,min,max):fallback;
    }
}
