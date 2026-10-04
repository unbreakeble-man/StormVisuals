package dev.stormvisuals.gui;

import dev.stormvisuals.module.CrosshairSettings;
import dev.stormvisuals.module.CrosshairPulse;
import dev.stormvisuals.module.CustomCrosshair;
import dev.stormvisuals.render.CrosshairRenderer;
import java.util.ArrayDeque;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Non-pausing editor: screen input never falls through to game actions. */
public final class CrosshairEditorScreen extends Screen {
    private final CrosshairControls controls=new CrosshairControls(this::beginEdit,this::endEdit);
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private String beforeEdit;
    private int selected,layerOffset;
    private boolean commonTab,testHover,closing,returnToGui,dragElement;
    private float panelX,panelY,panelScale,panelHeight,scroll,maxScroll,zoom=2,alpha=1;
    private float grabX,grabY;
    private long opened=System.nanoTime(),closed,controlsRevision=-1;
    private final CrosshairPulse previewAnimation=new CrosshairPulse();
    private CrosshairSettings.Kind newKind=CrosshairSettings.Kind.LINE;
    private int controlsSelection=-2;
    private boolean controlsTab;
    private float listTop,listHeight,toolbarTop,controlsTop;

    public CrosshairEditorScreen(){super(Component.literal("Редактор прицела"));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}

    private void dimensions(){
        float available=Math.max(20,width*.5f-16);
        panelScale=Math.max(.01f,Math.min(1,Math.min(available/310,(height-16)/360f)));
        panelHeight=(height-16)/panelScale;
        panelY=8;
        float time=closing?(System.nanoTime()-closed)/160_000_000f:(System.nanoTime()-opened)/200_000_000f;
        float t=Math.clamp(time,0,1);float eased=t*t*(3-2*t);alpha=closing?1-eased:eased;
        panelX=width-310*panelScale-8+(1-alpha)*12*panelScale;
    }
    private CrosshairSettings.Element element(){
        var list=CustomCrosshair.settings().elements;
        return selected>=0&&selected<list.size()?list.get(selected):null;
    }
    private void rebuildControls(){
        if(controlsRevision==CustomCrosshair.revision()&&controlsSelection==selected&&controlsTab==commonTab)return;
        controlsRevision=CustomCrosshair.revision();controlsSelection=selected;controlsTab=commonTab;
        controls.clear();
        if(commonTab){controls.common();return;}
        controls.choice("Новый элемент",Arrays.stream(CrosshairSettings.Kind.values()).map(k->k.label).toArray(String[]::new),()->newKind.ordinal(),i->newKind=CrosshairSettings.Kind.values()[i]);
        CrosshairSettings.Element e=element();if(e==null)return;
        controls.choice("Форма элемента",Arrays.stream(CrosshairSettings.Kind.values()).map(k->k.label).toArray(String[]::new),()->e.kind.ordinal(),i->e.kind=CrosshairSettings.Kind.values()[i]);
        controls.toggle("Видимость",()->e.visible,v->e.visible=v);
        controls.number("Положение X",-64,64,.25f,()->e.x,v->e.x=v);
        controls.number("Положение Y",-64,64,.25f,()->e.y,v->e.y=v);
        controls.number("Поворот",-180,180,1,()->e.rotation,v->e.rotation=v);
        controls.number(e.kind==CrosshairSettings.Kind.LINE?"Длина":"Размер",.5f,64,.25f,()->e.size,v->e.size=v);
        if(e.kind!=CrosshairSettings.Kind.DOT)controls.number("Толщина линий",.25f,8,.25f,()->e.thickness,v->e.thickness=v);
        if(e.kind==CrosshairSettings.Kind.SQUARE)controls.toggle("Заливка квадрата",()->e.filled,v->e.filled=v);
        controls.number("Прозрачность элемента",0,100,1,()->e.opacity,v->e.opacity=v);
        controls.toggle("Свои настройки",()->e.ownSettings,v->{
            if(v&&!e.ownSettings){var s=CustomCrosshair.settings();e.color=s.color;e.outline=s.outline;e.outlineColor=s.outlineColor;e.outlineWidth=s.outlineWidth;}
            e.ownSettings=v;
        });
        if(e.ownSettings){
            controls.color("Цвет элемента",()->e.color,v->e.color=v);
            controls.toggle("Обводка элемента",()->e.outline,v->e.outline=v);
            if(e.outline){controls.color("Цвет обводки элемента",()->e.outlineColor,v->e.outlineColor=v);controls.number("Толщина обводки элемента",.25f,4,.25f,()->e.outlineWidth,v->e.outlineWidth=v);}
        }
    }
    private void layout(){
        int count=CustomCrosshair.settings().elements.size();
        selected=Math.clamp(selected,-1,count-1);
        rebuildControls();
        layerOffset=Math.clamp(layerOffset,0,Math.max(0,count-6));
        listTop=216;listHeight=Math.min(6,count)*24;
        toolbarTop=listTop+listHeight+6;
        controlsTop=commonTab?194:toolbarTop+36;
        maxScroll=Math.max(0,controlsTop+controls.height()+10-(panelHeight-70));
        scroll=Math.clamp(scroll,0,maxScroll);
        controls.position(12,controlsTop,286);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        dimensions();layout();
        float pulse=previewPulse();
        CrosshairRenderer.draw(g,width/2f,height/2f,1,testHover,pulse,1);
        g.pose().pushMatrix();g.pose().translate(panelX,panelY);g.pose().scale(panelScale,panelScale);
        float localX=(mx-panelX)/panelScale,localY=(my-panelY)/panelScale;
        box(g,0,0,310,panelHeight,14,0xEC121419);
        text(g,"Редактор прицела",12,14,15,0xFFFFFFFF);
        button(g,10,38,140,26,"Элементы",!commonTab);
        button(g,158,38,142,26,"Общие",commonTab);
        box(g,10,70,290,114,8,0xB0080B10);
        g.enableScissor(10,70,300,184);
        box(g,154,70,1,114,0,0x20FFFFFF);box(g,10,126,290,1,0,0x20FFFFFF);
        CrosshairRenderer.draw(g,155,127,zoom,testHover,pulse,alpha);
        drawSelection(g);
        g.disableScissor();
        text(g,String.format(java.util.Locale.ROOT,"Предпросмотр ×%.1f · колесо: масштаб",zoom),18,173,9,0x90FFFFFF);
        g.enableScissor(8,190,302,(int)(panelHeight-70));
        g.pose().pushMatrix();g.pose().translate(0,-scroll);
        if(!commonTab)drawLayers(g);
        controls.render(g,12,controlsTop,286,localX,localY+scroll,alpha);
        g.pose().popMatrix();g.disableScissor();
        if(maxScroll>0){float visible=panelHeight-260;float track=Math.max(1,visible);float handle=Math.max(12,track*visible/(visible+maxScroll));
            box(g,303,190+(track-handle)*scroll/maxScroll,2,handle,1,0x80408CFF);}
        float footer=panelHeight-66;
        box(g,10,footer-4,290,1,0,0x20FFFFFF);
        button(g,10,footer,140,24,"Проверить цвет",testHover);
        button(g,158,footer,142,24,"Проверить импульс",false);
        button(g,10,footer+30,140,26,"Назад к модулям",false);
        button(g,158,footer+30,142,26,"Готово",true);
        if(CustomCrosshair.saveError()!=null)text(g,CustomCrosshair.saveError(),12,31,9,0xFFFF7777);
        g.pose().popMatrix();
    }
    private void drawLayers(GuiGraphics g){
        var list=CustomCrosshair.settings().elements;
        text(g,"Элементы "+list.size()+"/32 · Ctrl+Z / Ctrl+Y",12,198,10,0xBFFFFFFF);
        for(int i=layerOffset;i<Math.min(list.size(),layerOffset+6);i++){
            var e=list.get(i);float y=listTop+(i-layerOffset)*24;
            box(g,12,y,286,22,4,i==selected?0x48408CFF:0x10FFFFFF);
            text(g,(i+1)+". "+e.kind.label+(e.visible?"":" · скрыт"),20,y+7,10,e.visible?0xFFFFFFFF:0x80FFFFFF);
        }
        button(g,12,toolbarTop,44,26,"+",list.size()<32);
        button(g,62,toolbarTop,80,26,"Копия",false);
        button(g,148,toolbarTop,50,26,"−",false);
        button(g,204,toolbarTop,44,26,"↑",false);
        button(g,254,toolbarTop,44,26,"↓",false);
    }
    private void drawSelection(GuiGraphics g){
        var e=element();if(e==null||commonTab)return;
        float scale=1+previewPulse()*.25f;
        if(e.kind==CrosshairSettings.Kind.DOT&&e.x==0&&e.y==0)scale=1;
        float w=e.size*scale,h=e.kind==CrosshairSettings.Kind.LINE?e.thickness:w;
        g.pose().pushMatrix();g.pose().translate(155+e.x*zoom*scale,127+e.y*zoom*scale);g.pose().rotate((float)Math.toRadians(e.rotation));
        float padding=4;
        float bx=w*zoom/2+padding,by=h*zoom/2+padding;
        box(g,-bx,-by,bx*2,1,0,0xFF78ADFF);box(g,-bx,by,bx*2,1,0,0xFF78ADFF);
        box(g,-bx,-by,1,by*2,0,0xFF78ADFF);box(g,bx,-by,1,by*2,0,0xFF78ADFF);
        g.pose().popMatrix();
    }
    private float previewPulse(){
        return previewAnimation.value(System.nanoTime());
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(closing)return true;
        dimensions();layout();controls.finish();
        float x=(float)(e.x()-panelX)/panelScale,y=(float)(e.y()-panelY)/panelScale;
        if(e.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return true;
        if(hit(x,y,10,38,290,26)){commonTab=x>154;controls.cancel();scroll=0;return true;}
        float footer=panelHeight-66;
        if(hit(x,y,10,footer,140,24)){testHover=!testHover;return true;}
        if(hit(x,y,158,footer,142,24)){previewAnimation.trigger(System.nanoTime());return true;}
        if(hit(x,y,10,footer+30,140,26)){returnToGui=true;onClose();return true;}
        if(hit(x,y,158,footer+30,142,26)){onClose();return true;}
        if(hit(x,y,10,70,290,114)&&!commonTab){
            previewAnimation.reset();
            selected=pick((x-155)/zoom,(y-127)/zoom);controls.cancel();
            var element=element();if(element!=null){beginEdit();dragElement=true;grabX=(x-155)/zoom-element.x;grabY=(y-127)/zoom-element.y;}
            return true;
        }
        if(y<190||y>=panelHeight-70)return true;
        y+=scroll;
        if(!commonTab){
            if(hit(x,y,12,listTop,286,listHeight)){selected=layerOffset+(int)((y-listTop)/24);controls.cancel();return true;}
            if(hit(x,y,12,toolbarTop,286,26)){
                if(x<56){if(CustomCrosshair.settings().elements.size()<32)mutate(()->{CustomCrosshair.settings().elements.add(new CrosshairSettings.Element(newKind));selected=CustomCrosshair.settings().elements.size()-1;layerOffset=Math.max(0,selected-5);});}
                else if(x>=62&&x<142){if(element()!=null&&CustomCrosshair.settings().elements.size()<32)mutate(()->{CustomCrosshair.settings().elements.add(selected+1,element().copy());selected++;layerOffset=Math.max(0,selected-5);});}
                else if(x>=148&&x<198){if(element()!=null)mutate(()->CustomCrosshair.settings().elements.remove(selected));}
                else if(x>=204&&x<248)move(-1);
                else if(x>=254)move(1);
                controls.cancel();return true;
            }
        }
        controls.click(x,y,e.button());return true;
    }
    private int pick(float x,float y){
        var list=CustomCrosshair.settings().elements;
        for(int i=list.size()-1;i>=0;i--){var e=list.get(i);if(!e.visible)continue;
            float a=(float)Math.toRadians(-e.rotation),dx=x-e.x,dy=y-e.y;
            float px=dx*(float)Math.cos(a)-dy*(float)Math.sin(a),py=dx*(float)Math.sin(a)+dy*(float)Math.cos(a);
            float tolerance=3/zoom,half=e.size/2;
            boolean match=switch(e.kind){
                case LINE->Math.abs(px)<=half+tolerance&&Math.abs(py)<=e.thickness/2+tolerance;
                case DOT->Math.hypot(px,py)<=half+tolerance;
                case RING->Math.abs(Math.hypot(px,py)-(half-e.thickness/2))<=e.thickness/2+tolerance;
                case SQUARE->Math.max(Math.abs(px),Math.abs(py))<=half+tolerance&&(e.filled||Math.max(Math.abs(px),Math.abs(py))>=half-e.thickness-tolerance);
                case V->segment(px,py,-half,-half,0,0)<=e.thickness/2+tolerance||segment(px,py,half,-half,0,0)<=e.thickness/2+tolerance;
            };
            if(match)return i;
        }
        return -1;
    }
    private static float segment(float x,float y,float ax,float ay,float bx,float by){float dx=bx-ax,dy=by-ay,t=Math.clamp(((x-ax)*dx+(y-ay)*dy)/Math.max(.0001f,dx*dx+dy*dy),0,1);return(float)Math.hypot(x-ax-dx*t,y-ay-dy*t);}
    private void move(int direction){int target=selected+direction;if(element()==null||target<0||target>=CustomCrosshair.settings().elements.size())return;mutate(()->{java.util.Collections.swap(CustomCrosshair.settings().elements,selected,target);selected=target;layerOffset=Math.clamp(layerOffset,Math.max(0,selected-5),selected);});}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(closing)return true;
        float x=(float)(e.x()-panelX)/panelScale,y=(float)(e.y()-panelY)/panelScale;
        if(dragElement){var item=element();if(item!=null){item.x=snap((x-155)/zoom-grabX);item.y=snap((y-127)/zoom-grabY);CustomCrosshair.changed();}return true;}
        controls.drag(x,y+scroll);return true;
    }
    private static float snap(float v){return Math.clamp(Math.round(v*4)/4f,-64,64);}
    @Override public boolean mouseReleased(MouseButtonEvent e){if(dragElement){dragElement=false;endEdit();}controls.release();return true;}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(closing||dragElement||controls.busy())return true;
        float x=(float)(mx-panelX)/panelScale,y=(float)(my-panelY)/panelScale;
        if(hit(x,y,10,70,290,114)){zoom=Math.clamp(zoom+(float)vertical*.25f,.5f,8);return true;}
        if(!commonTab&&hit(x,y+scroll,12,listTop,286,listHeight))layerOffset=Math.clamp(layerOffset-(int)vertical,0,Math.max(0,CustomCrosshair.settings().elements.size()-6));
        else if(hit(x,y,0,190,310,panelHeight-260))scroll=Math.clamp(scroll-(float)vertical*32,0,maxScroll);
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e){
        if(closing)return true;
        if(e.isEscape()){onClose();return true;}
        if(controls.typing()){controls.key(e);return true;}
        if(e.hasControlDown()&&(e.key()==GLFW.GLFW_KEY_Z||e.key()==GLFW.GLFW_KEY_Y)){history(e.key()==GLFW.GLFW_KEY_Y||e.hasShiftDown());return true;}
        if(e.key()==GLFW.GLFW_KEY_DELETE&&element()!=null){mutate(()->CustomCrosshair.settings().elements.remove(selected));controls.cancel();}
        return true;
    }
    @Override public boolean charTyped(CharacterEvent e){controls.character(e);return true;}
    private void beginEdit(){if(beforeEdit==null)beforeEdit=CustomCrosshair.snapshot();}
    private void endEdit(){if(beforeEdit==null)return;String old=beforeEdit;beforeEdit=null;if(!old.equals(CustomCrosshair.snapshot())){undo.addLast(old);if(undo.size()>64)undo.removeFirst();redo.clear();}}
    private void mutate(Runnable action){controls.finish();beginEdit();action.run();CustomCrosshair.changed();endEdit();}
    private void history(boolean forward){
        controls.finish();if(dragElement){dragElement=false;endEdit();}controls.cancel();
        var source=forward?redo:undo;var destination=forward?undo:redo;if(source.isEmpty())return;
        destination.addLast(CustomCrosshair.snapshot());CustomCrosshair.restore(source.removeLast());controlsRevision=-1;
    }
    @Override public void onClose(){if(closing)return;controls.finish();if(dragElement){dragElement=false;endEdit();}CustomCrosshair.flush();closing=true;closed=System.nanoTime();}
    @Override public void tick(){if(minecraft.player==null||minecraft.level==null){CustomCrosshair.flush();minecraft.setScreen(null);return;}if(closing&&System.nanoTime()-closed>=160_000_000)minecraft.setScreen(returnToGui?ClickGuiScreen.openCrosshair():null);}
    @Override public void removed(){controls.finish();CustomCrosshair.flush();super.removed();}
    private static boolean hit(float x,float y,float left,float top,float w,float h){return x>=left&&y>=top&&x<left+w&&y<top+h;}
    private int tint(int c){return Math.round((c>>>24)*alpha)<<24|c&0xFFFFFF;}
    private void box(GuiGraphics g,float x,float y,float w,float h,float r,int c){CrosshairControls.box(g,x,y,w,h,r,tint(c));}
    private void text(GuiGraphics g,String t,float x,float y,float size,int c){CrosshairControls.text(g,t,x,y,size,tint(c));}
    private void button(GuiGraphics g,float x,float y,float w,float h,String label,boolean active){box(g,x,y,w,h,5,active?0x50408CFF:0x20FFFFFF);text(g,label,x+8,y+8,10,0xFFFFFFFF);}
}
