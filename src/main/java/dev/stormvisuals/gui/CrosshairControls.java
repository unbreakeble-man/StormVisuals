package dev.stormvisuals.gui;

import dev.stormvisuals.module.CrosshairSettings;
import dev.stormvisuals.module.CustomCrosshair;
import dev.stormvisuals.render.MtsdfFont;
import dev.stormvisuals.render.PngIconRenderer;
import dev.stormvisuals.render.RoundedRectRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Controls shared by the module card and editor, in their host's local coordinates. */
final class CrosshairControls {
    private enum Type { NUMBER, TOGGLE, COLOR, CHOICE, BUTTON }
    private static final class Row {
        String label; Type type; Supplier<Float> value; Consumer<Float> setter;
        Supplier<Boolean> flag; Consumer<Boolean> toggle; IntSupplier rgb; IntConsumer paint;
        String[] options; IntSupplier selected; IntConsumer choose; Runnable action;
        float min,max,step,y;
    }
    private final List<Row> rows=new ArrayList<>();
    private final Map<String,DropMotion> dropdowns=new HashMap<>();
    private static final Identifier ARROW=Identifier.fromNamespaceAndPath("stormvisuals","textures/gui/icons/dropdown.png");
    private static final class DropMotion {
        float value;
        long time=System.nanoTime();
        float update(boolean open){long now=System.nanoTime();float dt=(now-time)/1_000_000_000f;time=now;float target=open?1:0;
            value+=(target-value)*(1-(float)Math.exp(-24*dt));if(Math.abs(value-target)<.001f)value=target;return value;}
    }
    private final Runnable before,after,onChanged;
    private Row dragging,field;
    private String expanded="", fieldKey="", text="";
    private boolean replaceText;
    private int dragPart;
    private float x,y,width,alpha=1,hue,saturation,value;
    CrosshairControls(Runnable before,Runnable after){this(before,after,CustomCrosshair::changed);}
    CrosshairControls(Runnable before,Runnable after,Runnable onChanged){this.before=before;this.after=after;this.onChanged=onChanged;}
    void clear(){rows.clear();}
    void number(String label,float min,float max,float step,Supplier<Float> getter,Consumer<Float> setter){
        Row r=row(label,Type.NUMBER);r.min=min;r.max=max;r.step=step;r.value=getter;r.setter=setter;
    }
    void toggle(String label,Supplier<Boolean> getter,Consumer<Boolean> setter){Row r=row(label,Type.TOGGLE);r.flag=getter;r.toggle=setter;}
    void color(String label,IntSupplier getter,IntConsumer setter){Row r=row(label,Type.COLOR);r.rgb=getter;r.paint=setter;}
    void choice(String label,String[] options,IntSupplier getter,IntConsumer setter){Row r=row(label,Type.CHOICE);r.options=options;r.selected=getter;r.choose=setter;}
    void button(String label,Runnable action){Row r=row(label,Type.BUTTON);r.action=action;}
    private Row row(String label,Type type){Row r=new Row();r.label=label;r.type=type;rows.add(r);if(fieldKey.equals(label))field=r;return r;}
    void common(){
        CrosshairSettings s=CustomCrosshair.settings();
        color("Цвет прицела",()->s.color,v->s.color=v);
        number("Прозрачность",0,100,1,()->s.opacity,v->s.opacity=v);
        toggle("Обводка",()->s.outline,v->s.outline=v);
        if(s.outline){color("Цвет обводки",()->s.outlineColor,v->s.outlineColor=v);number("Толщина обводки",.25f,4,.25f,()->s.outlineWidth,v->s.outlineWidth=v);}
        toggle("Окрашивать при наводке",()->s.hover,v->s.hover=v);
        if(s.hover)color("Цвет при наводке",()->s.hoverColor,v->s.hoverColor=v);
        if(s.style!=CrosshairSettings.Style.V){
            toggle("Динамический",()->s.dynamic,v->{s.dynamic=v;CustomCrosshair.resetPulse();});
            if(s.dynamic)toggle("Только с мечом или топором",()->s.weaponsOnly,v->s.weaponsOnly=v);
        }
    }
    void preset(Runnable edit){
        CrosshairSettings s=CustomCrosshair.settings();
        choice("Вид прицела",java.util.Arrays.stream(CrosshairSettings.Style.values()).map(v->v.label).toArray(String[]::new),()->s.style.ordinal(),v->{s.style=CrosshairSettings.Style.values()[v];CustomCrosshair.resetPulse();});
        switch(s.style){
            case SQUARE->number("Размер квадрата",2,64,.25f,()->s.squareSize,v->s.squareSize=v);
            case RING->number("Диаметр кольца",2,64,.25f,()->s.ringSize,v->s.ringSize=v);
            case V->number("Размер V",2,64,.25f,()->s.vSize,v->s.vSize=v);
            case CROSS->{number("Длина линий",.5f,64,.25f,()->s.crossLength,v->s.crossLength=v);number("Ширина линий",.25f,8,.25f,()->s.crossWidth,v->s.crossWidth=v);number("Зазор от центра",0,32,.25f,()->s.crossGap,v->s.crossGap=v);}
            case CUSTOM->button("Создать кастомный прицел",edit);
        }
        common();
    }
    float height(){float h=0;for(Row r:rows)h+=rowHeight(r);return h;}
    void position(float x,float y,float width){this.x=x;this.y=y;this.width=width;float top=y;for(Row r:rows){r.y=top;top+=rowHeight(r);}}
    private float choiceProgress(Row r){return dropdowns.computeIfAbsent(r.label,k->new DropMotion()).update(r.label.equals(expanded));}
    private float rowHeight(Row r){return r.type==Type.NUMBER?48:r.type==Type.CHOICE?32+(4+r.options.length*26)*choiceProgress(r):r.type==Type.COLOR&&r.label.equals(expanded)?164:32;}
    void render(GuiGraphics g,float x,float y,float width,float mouseX,float mouseY,float alpha){
        position(x,y,width);this.alpha=alpha;
        float top=y;
        for(Row r:rows){
            r.y=top;
            if(r.type==Type.BUTTON){rect(g,x,top+2,width,26,6,0x38408CFF);label(g,r.label,x+8,top+10,11,0xFFFFFFFF);}
            else{
                label(g,r.label,x,top+9,11,0xDDFFFFFF);
                switch(r.type){
                    case TOGGLE->{boolean on=r.flag.get();rect(g,x+width-32,top+6,32,18,9,on?0xFF408CFF:0x30FFFFFF);rect(g,x+width-(on?16:29),top+9,12,12,6,0xFFFFFFFF);}
                    case NUMBER->{
                        SliderValue.draw(g,fieldKey.equals(r.label)?text:format(r.value.get()),x+width,top+3,alpha);
                        float progress=Math.clamp((r.value.get()-r.min)/(r.max-r.min),0,1);
                        rect(g,x,top+35,width,3,1,0x30FFFFFF);if(progress>0)rect(g,x,top+35,width*progress,3,1,0xFF408CFF);
                        rect(g,x+width*progress-4,top+32,9,9,4,0xFFFFFFFF);
                    }
                    case CHOICE->{
                        float left=x+width-148,progress=choiceProgress(r),listHeight=(4+r.options.length*26)*progress;
                        boolean over=mouseX>=left&&mouseX<left+148&&mouseY>=top+3&&mouseY<top+27;
                        // Same continuous, right-aligned dropdown as the existing Click GUI controls.
                        rect(g,left,top+3,148,24+listHeight,6,over?0xFF292A2D:0xFF202124);
                        label(g,r.options[r.selected.getAsInt()],left+9,top+10,11,0xFFFFFFFF);
                        g.pose().pushMatrix();g.pose().translate(left+134,top+15);g.pose().rotate(progress*(float)Math.PI);
                        PngIconRenderer.draw(g,ARROW,-6,-6,12,withAlpha(0x80FFFFFF));g.pose().popMatrix();
                        if(listHeight>.1f){
                            g.enableScissor((int)left,(int)(top+27),(int)(left+148),(int)Math.ceil(top+27+listHeight));
                            for(int i=0;i<r.options.length;i++){
                                float optionY=top+30+i*26;
                                boolean hover=r.label.equals(expanded)&&mouseX>=left+4&&mouseX<left+144&&mouseY>=optionY&&mouseY<optionY+24;
                                boolean selected=i==r.selected.getAsInt();
                                if(selected||hover)rect(g,left+4,optionY,140,24,4,selected?(hover?0x48408CFF:0x30408CFF):0x16FFFFFF);
                                label(g,r.options[i],left+9,optionY+7,11,0xFFFFFFFF);
                            }
                            g.disableScissor();
                        }
                    }
                    case COLOR->{
                        int rgb=r.rgb.getAsInt();rect(g,x+width-40,top+6,36,18,4,0xFF000000|rgb);
                        if(r.label.equals(expanded))drawPalette(g,r,top);
                    }
                    default->{}
                }
            }
            top+=rowHeight(r);
        }
    }
    private void drawPalette(GuiGraphics g,Row r,float top){
        float left=x,sy=top+34,w=width-4,h=76;
        int hueRGB=Color.HSBtoRGB(hue,1,1);
        // Only an open picker submits these strips; no textures or GPU resources are created.
        for(int i=0;i<64;i++){
            int rgb=mixRGB(0xFFFFFF,hueRGB,i/63f);
            rect(g,left+w*i/64,sy,w/64+.1f,h,0,0xFF000000|rgb);
        }
        g.fillGradient((int)left,(int)sy,(int)(left+w),(int)(sy+h),0x00000000,withAlpha(0xFF000000));
        rect(g,left+saturation*w-2,sy+(1-value)*h-2,4,4,2,0xFFFFFFFF);
        for(int i=0;i<48;i++)rect(g,left+w*i/48,sy+82,w/48+.1f,10,0,Color.HSBtoRGB(i/47f,1,1));
        rect(g,left+hue*w-1,sy+80,2,14,1,0xFFFFFFFF);
        rect(g,left,sy+100,w,24,5,0x15FFFFFF);
        label(g,fieldKey.equals(r.label)?text:String.format(Locale.ROOT,"#%06X",r.rgb.getAsInt()),left+8,sy+107,11,0xFFFFFFFF);
    }
    boolean click(float mx,float my,int button){
        if(button!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return false;
        finish();
        for(Row r:rows){if(mx<x||mx>=x+width||my<r.y||my>=r.y+rowHeight(r))continue;
            float local=my-r.y;
            switch(r.type){
                case BUTTON->r.action.run();
                case TOGGLE->{before.run();r.toggle.accept(!r.flag.get());changed();after.run();}
                case NUMBER->{
                    if(local>=3 && local<3+SliderValue.HEIGHT && mx>=x+width-SliderValue.WIDTH){focus(r,format(r.value.get()));}
                    else if(local>=28){before.run();dragging=r;dragPart=0;drag(mx,my);}
                }
                case CHOICE->{
                    if(mx<x+width-148)return true;
                    if(local<27)expanded=expanded.equals(r.label)?"":r.label;
                    else if(local>=30&&expanded.equals(r.label)&&choiceProgress(r)>.9f){int index=(int)((local-30)/26);if(index<r.options.length){before.run();r.choose.accept(index);expanded="";changed();after.run();}}
                }
                case COLOR->{
                    if(local<32){expanded=expanded.equals(r.label)?"":r.label;syncColor(r);}
                    else if(local>=134){focus(r,String.format(Locale.ROOT,"#%06X",r.rgb.getAsInt()));}
                    else if(local>=34&&local<110){before.run();dragging=r;dragPart=1;drag(mx,my);}
                    else if(local>=116&&local<130){before.run();dragging=r;dragPart=2;drag(mx,my);}
                }
            }
            return true;
        }
        return false;
    }
    boolean drag(float mx,float my){
        if(dragging==null)return false;
        Row r=dragging;
        if(dragPart==0){float n=Math.round((r.min+Math.clamp((mx-x)/width,0,1)*(r.max-r.min))/r.step)*r.step;r.setter.accept(Math.clamp(n,r.min,r.max));}
        else{
            if(dragPart==1){saturation=Math.clamp((mx-x)/(width-4),0,1);value=1-Math.clamp((my-r.y-34)/76,0,1);}
            else hue=Math.clamp((mx-x)/(width-4),0,1);
            r.paint.accept(Color.HSBtoRGB(hue,saturation,value)&0xFFFFFF);
        }
        changed();return true;
    }
    boolean release(){if(dragging==null)return false;dragging=null;after.run();return true;}
    boolean busy(){return dragging!=null||field!=null;}
    boolean typing(){return field!=null;}
    private void focus(Row r,String initial){field=r;fieldKey=r.label;text=initial;replaceText=true;}
    void finish(){
        release();
        if(field==null)return;
        Row r=field;field=null;fieldKey="";
        try{
            if(r.type==Type.NUMBER){float n=Float.parseFloat(text.replace(',','.'));if(!Float.isFinite(n))return;
                n=Math.clamp(Math.round(n/r.step)*r.step,r.min,r.max);if(n!=r.value.get()){before.run();r.setter.accept(n);changed();after.run();}}
            else{String hex=text.trim().replace("#","");if(!hex.matches("[0-9a-fA-F]{6}"))return;
                int rgb=Integer.parseInt(hex,16);if(rgb!=r.rgb.getAsInt()){before.run();r.paint.accept(rgb);syncColor(r);changed();after.run();}}
        }catch(NumberFormatException ignored){}
    }
    void cancel(){dragging=null;field=null;fieldKey="";expanded="";}
    boolean closePopup(){if(expanded.isEmpty())return false;finish();expanded="";return true;}
    boolean key(KeyEvent e){
        if(field==null)return false;
        if(e.key()==GLFW.GLFW_KEY_ENTER||e.key()==GLFW.GLFW_KEY_KP_ENTER){finish();return true;}
        if(e.isSelectAll()){replaceText=true;return true;}
        if(e.key()==GLFW.GLFW_KEY_BACKSPACE){text=replaceText?"":text.isEmpty()?"":text.substring(0,text.length()-1);replaceText=false;return true;}
        return !e.isEscape();
    }
    boolean character(CharacterEvent e){
        if(field==null)return false;
        String part=e.codepointAsString();
        if(part.matches(field.type==Type.COLOR?"[0-9a-fA-F#]":"[0-9.,+-]")){if(replaceText){text="";replaceText=false;}if(text.length()<16)text+=part;}
        return true;
    }
    private void syncColor(Row r){float[] hsv=Color.RGBtoHSB(r.rgb.getAsInt()>>16&255,r.rgb.getAsInt()>>8&255,r.rgb.getAsInt()&255,null);hue=hsv[0];saturation=hsv[1];value=hsv[2];}
    private void changed(){onChanged.run();}
    private static String format(float n){return String.format(Locale.ROOT,"%.2f",n).replaceAll("0+$","").replaceAll("\\.$","");}
    private static int mixRGB(int a,int b,float t){int out=0;for(int shift=0;shift<24;shift+=8)out|=Math.round((a>>shift&255)*(1-t)+(b>>shift&255)*t)<<shift;return out;}
    private int withAlpha(int c){return Math.round((c>>>24)*alpha)<<24|c&0xFFFFFF;}
    private void rect(GuiGraphics g,float x,float y,float w,float h,float radius,int c){box(g,x,y,w,h,radius,withAlpha(c));}
    private void label(GuiGraphics g,String text,float x,float y,float size,int c){text(g,text,x,y,size,withAlpha(c));}
    static void box(GuiGraphics g,float x,float y,float w,float h,float radius,int c){if(w>0&&h>0)RoundedRectRenderer.draw(g,new RoundedRectRenderer.Rect(x,y,w,h,radius,radius,radius,radius,c));}
    static void text(GuiGraphics g,String text,float x,float y,float size,int c){MtsdfFont.draw(g,text,x,y,size,c);}
}
